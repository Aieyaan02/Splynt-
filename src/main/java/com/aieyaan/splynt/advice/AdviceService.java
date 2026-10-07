package com.aieyaan.splynt.advice;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.insights.InsightsService;
import com.aieyaan.splynt.tenant.StoreRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

@Service
public class AdviceService {
    private final AdviceReportRepository reports;
    private final InsightsService insights;
    private final StoreRepository stores;
    private final OpenAiAdviceClient client;
    private final JsonMapper json = new JsonMapper();
    public AdviceService(AdviceReportRepository reports, InsightsService insights, StoreRepository stores, OpenAiAdviceClient client) {
        this.reports = reports; this.insights = insights; this.stores = stores; this.client = client;
    }
    @Transactional(readOnly = true)
    public View read(Long storeId) {
        return view(reports.findById(storeId).orElse(null), insights.read(storeId), null);
    }
    @Transactional
    public View generate(Long storeId) {
        // Serializes both the first insert and subsequent requests across application instances.
        // At most one bounded provider request per store per hour; no browser polling triggers it.
        stores.findLockedById(storeId).filter(s -> s.isActive()).orElseThrow();
        var source = insights.read(storeId);
        AdviceReport report = reports.findById(storeId).orElse(null);
        String unavailable = availability(source);
        if (unavailable != null) return view(report, source, unavailable);
        OffsetDateTime now = OffsetDateTime.now();
        if (report != null && report.attemptedAt.plusHours(1).isAfter(now)) return view(report, source, "A new report can be requested one hour after the last attempt.");
        if (report == null) report = new AdviceReport(storeId);
        report.attemptedAt = now;
        Map<String, Object> evidence = evidence(source);
        try {
            AdviceContent content = client.generate(evidence);
            report.reportJson = json.writeValueAsString(content);
            report.evidenceJson = json.writeValueAsString(evidence);
            report.generatedAt = now; report.model = client.model(); report.errorMessage = null;
        } catch (RuntimeException failure) {
            // Do not expose provider responses, credentials, prompts or exception details to clients/logs.
            report.errorMessage = "Recommendations could not be generated. Your inventory is unchanged. Try again after the cooldown or contact support.";
        }
        reports.saveAndFlush(report);
        return view(report, source, null);
    }
    static Map<String, Object> evidence(InsightsService.Insights source) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("window", Map.of("from", source.from().toString(), "untilExclusive", source.untilExclusive().toString(),
                "completeDays", source.summary().completeDays(), "orders", source.summary().orders(), "units", source.summary().units()));
        data.put("location", Map.of("location", source.location(), "timezone", source.timezone(), "meaning", "Store context only; no local market demand data"));
        data.put("timing", Map.of("hourlyUnits", source.summary().hourlyUnits(), "weekdayUnitsMondayFirst", source.summary().weekdayUnits(),
                "methodology", source.methodology()));
        data.put("seasonality", source.seasonalStatus());
        for (var product : source.summary().topProducts()) data.put("product-" + product.productId(), product);
        return data;
    }
    private String availability(InsightsService.Insights source) {
        if (!client.configured()) return "AI recommendations are not enabled for this installation yet.";
        if (source.lastSyncedAt() == null || source.lastSyncedAt().isBefore(OffsetDateTime.now().minusHours(24)))
            return "Refresh Clover sales before generating recommendations; the last successful import is missing or over 24 hours old.";
        if (source.syncError() != null) return "Resolve the sales import warning before generating recommendations.";
        if (!source.summary().sufficientForVelocity() || source.summary().topProducts().isEmpty())
            return "Recommendations need at least 14 complete days, 30 included orders and recognized product sales.";
        return null;
    }
    private View view(AdviceReport report, InsightsService.Insights source, String message) {
        OffsetDateTime next = report == null ? null : report.attemptedAt.plusHours(1);
        String unavailable = availability(source);
        boolean canGenerate = unavailable == null && (next == null || !next.isAfter(OffsetDateTime.now()));
        return new View(client.configured(), canGenerate, message != null ? message : unavailable,
                report == null ? null : report.generatedAt, next, report == null ? null : report.model,
                report == null ? null : report.errorMessage,
                report == null || report.reportJson == null ? null : json.readValue(report.reportJson, AdviceContent.class),
                report == null || report.evidenceJson == null ? null : json.readTree(report.evidenceJson));
    }
    public record View(boolean configured, boolean canGenerate, String message, OffsetDateTime generatedAt,
            OffsetDateTime nextGenerationAt, String model, String error, AdviceContent report, JsonNode evidence) {}
}
