package com.aieyaan.splynt.insights;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.clover.CloverOAuthCredentialRepository;
import com.aieyaan.splynt.product.*;
import com.aieyaan.splynt.tenant.*;

@Service
public class InsightsService {
    private final SalesEventRepository sales;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final CloverOAuthCredentialRepository connections;
    public InsightsService(SalesEventRepository sales, ProductRepository products, StoreRepository stores,
            CloverOAuthCredentialRepository connections) {
        this.sales = sales; this.products = products; this.stores = stores; this.connections = connections;
    }
    @Transactional(readOnly = true)
    public Insights read(Long storeId) {
        Store store = stores.findById(storeId).filter(Store::isActive).orElseThrow();
        var connection = connections.findByStoreId(storeId);
        var coverage = connection.map(c -> c.getSalesCoverageStart()).orElse(null);
        var synced = connection.map(c -> c.getSalesSyncedAt()).orElse(null);
        String syncError = connection.map(c -> c.getSalesSyncError()).orElse(null);
        ZoneId zone = ZoneId.of(store.getTimezone());
        AnalysisWindow window = analysisWindow(coverage, synced, zone, Instant.now());
        ZonedDateTime start = window.start().atStartOfDay(zone);
        ZonedDateTime end = window.end().atStartOfDay(zone);
        var events = sales.findWindow(storeId, YearMonth.from(end).minusMonths(24).atDay(1).atStartOfDay(zone).toOffsetDateTime(), end.toOffsetDateTime());
        Map<Long, Product> catalog = new HashMap<>();
        products.findAllByStoreIdAndActiveTrueOrderByNameAsc(storeId).forEach(p -> catalog.put(p.getId(), p));
        Summary summary = calculate(events, catalog, zone, start.toLocalDate(), end.toLocalDate());
        var history = HistoricalSalesAnalysis.calculate(events, catalog, zone, coverage, end.toLocalDate());
        String location = java.util.stream.Stream.of(store.getCity(), store.getState(), store.getCountryCode())
                .filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        return new Insights(store.getName(), location, zone.getId(), synced, syncError,
                start.toLocalDate(), end.toLocalDate(), summary,
                "Paid, non-refunded Clover order lines only. Timing reflects order creation, not checkout time. Only full local calendar days covered by the last successful import are included.",
                history.status(), history);
    }
    // A failed or stopped importer must not turn missing days into zero-sale days.
    static AnalysisWindow analysisWindow(OffsetDateTime coverage, OffsetDateTime synced, ZoneId zone, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate end = synced == null ? today : synced.atZoneSameInstant(zone).toLocalDate();
        if (end.isAfter(today)) end = today;
        if (coverage == null || synced == null) return new AnalysisWindow(end, end);
        ZonedDateTime localCoverage = coverage.atZoneSameInstant(zone);
        LocalDate firstCompleteDay = localCoverage.toLocalDate();
        if (!localCoverage.toInstant().equals(firstCompleteDay.atStartOfDay(zone).toInstant())) {
            firstCompleteDay = firstCompleteDay.plusDays(1);
        }
        LocalDate start = end.minusDays(30);
        if (firstCompleteDay.isAfter(start)) start = firstCompleteDay;
        if (start.isAfter(end)) start = end;
        return new AnalysisWindow(start, end);
    }
    record AnalysisWindow(LocalDate start, LocalDate end) {}
    static Summary calculate(List<SalesEvent> events, Map<Long, Product> catalog, ZoneId zone, LocalDate start, LocalDate end) {
        int days = (int) ChronoUnit.DAYS.between(start, end);
        Map<Long, BigDecimal> totals = new HashMap<>();
        BigDecimal[] hourly = new BigDecimal[24]; Arrays.fill(hourly, BigDecimal.ZERO);
        BigDecimal[] weekdays = new BigDecimal[7]; Arrays.fill(weekdays, BigDecimal.ZERO);
        Set<String> orders = new HashSet<>();
        for (SalesEvent event : events) {
            var local = event.getOccurredAt().atZoneSameInstant(zone);
            if (local.toLocalDate().isBefore(start) || !local.toLocalDate().isBefore(end)) continue;
            totals.merge(event.getProductId(), event.getUnits(), BigDecimal::add);
            hourly[local.getHour()] = hourly[local.getHour()].add(event.getUnits());
            int day = local.getDayOfWeek().getValue() - 1;
            weekdays[day] = weekdays[day].add(event.getUnits());
            orders.add(event.getOrderId());
        }
        boolean sufficient = days >= 14 && orders.size() >= 30;
        List<ProductInsight> leaders = totals.entrySet().stream().filter(e -> catalog.containsKey(e.getKey()))
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed()).limit(10)
                .map(e -> {
                    Product p = catalog.get(e.getKey());
                    BigDecimal velocity = sufficient ? e.getValue().divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP) : null;
                    BigDecimal cover = velocity == null || velocity.signum() == 0 ? null
                            : BigDecimal.valueOf(p.getQuantity()).divide(velocity, 1, RoundingMode.HALF_UP);
                    return new ProductInsight(p.getId(), p.getName(), e.getValue(), velocity, cover, p.getQuantity());
                }).toList();
        BigDecimal total = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(days, orders.size(), total, sufficient, leaders, List.of(hourly), List.of(weekdays));
    }
    public record ProductInsight(Long productId, String name, BigDecimal units, BigDecimal unitsPerDay, BigDecimal estimatedDaysRemaining, int currentStock) {}
    public record Summary(int completeDays, int orders, BigDecimal units, boolean sufficientForVelocity,
            List<ProductInsight> topProducts, List<BigDecimal> hourlyUnits, List<BigDecimal> weekdayUnits) {}
    public record Insights(String storeName, String location, String timezone, OffsetDateTime lastSyncedAt, String syncError,
            LocalDate from, LocalDate untilExclusive, Summary summary, String methodology, String seasonalStatus, HistoricalSalesAnalysis.History history) {}
}
