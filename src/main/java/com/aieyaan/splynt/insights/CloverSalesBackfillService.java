package com.aieyaan.splynt.insights;

import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.clover.*;
import com.aieyaan.splynt.tenant.StoreRepository;

@Service
public class CloverSalesBackfillService {
    private final StoreRepository stores;
    private final CloverOAuthCredentialRepository credentials;
    private final CloverInventoryClient client;
    private final CloverSalesSyncService sales;
    public CloverSalesBackfillService(StoreRepository stores, CloverOAuthCredentialRepository credentials,
            CloverInventoryClient client, CloverSalesSyncService sales) {
        this.stores = stores; this.credentials = credentials; this.client = client; this.sales = sales;
    }
    @Transactional
    public void request(Long storeId) {
        var store = stores.findLockedById(storeId).filter(s -> s.isActive()).orElseThrow();
        var connection = credentials.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Connect Clover before importing older sales."));
        var target = LocalDate.now(ZoneId.of(store.getTimezone())).withDayOfMonth(1).minusMonths(24)
                .atStartOfDay(ZoneId.of(store.getTimezone())).toOffsetDateTime();
        connection.requestSalesBackfill(target);
    }
    @Transactional
    public void cancel(Long storeId) {
        stores.findLockedById(storeId).orElseThrow();
        credentials.findByStoreId(storeId).ifPresent(CloverOAuthCredential::cancelSalesBackfill);
    }
    @Transactional
    public void step(Long storeId) {
        stores.findLockedById(storeId).filter(s -> s.isActive()).orElseThrow();
        var connection = credentials.findByStoreId(storeId).orElseThrow();
        var target = connection.getSalesBackfillTarget();
        var end = connection.getSalesCoverageStart();
        if (target == null || end == null) return; // Wait for the initial recent-sales import.
        if (!end.isAfter(target)) { connection.cancelSalesBackfill(); return; }
        // Provider timestamps are milliseconds. Round the legacy upper bound up to avoid a sub-millisecond gap.
        var roundedEnd = end.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (roundedEnd.isBefore(end)) roundedEnd = roundedEnd.plusNanos(1_000_000);
        end = roundedEnd;
        var start = end.minusDays(30).isBefore(target) ? target : end.minusDays(30);
        var orders = client.getOrdersCreatedBetween(storeId, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli()).path("elements");
        if (!orders.isArray()) throw new IllegalStateException("Invalid historical order response");
        for (var order : orders) {
            long created = order.path("createdTime").asLong(0);
            if (created < start.toInstant().toEpochMilli() || created >= end.toInstant().toEpochMilli())
                throw new IllegalStateException("Historical order lies outside the requested range");
        }
        int skipped = sales.importOrders(storeId, orders, start, OffsetDateTime.now());
        if (skipped > 0) throw new IllegalStateException("Historical sales include unsupported or unmatched lines");
        // Only a fully imported chunk expands coverage. Failure rolls back events and progress together.
        connection.completeSalesBackfillStep(start);
    }
}
