package com.aieyaan.splynt.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.clover.*;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.tenant.StoreRepository;
import tools.jackson.databind.JsonNode;

@Service
public class CloverSalesSyncService {
    private final CloverInventoryClient client;
    private final CloverOAuthCredentialRepository credentials;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final SalesEventRepository sales;
    public CloverSalesSyncService(CloverInventoryClient client, CloverOAuthCredentialRepository credentials,
            StoreRepository stores, ProductRepository products, SalesEventRepository sales) {
        this.client = client; this.credentials = credentials; this.stores = stores; this.products = products; this.sales = sales;
    }
    @Transactional
    public void synchronize(Long storeId) {
        stores.findLockedById(storeId).filter(s -> s.isActive()).orElseThrow();
        var connection = credentials.findByStoreId(storeId).orElseThrow();
        OffsetDateTime started = OffsetDateTime.now();
        OffsetDateTime coverage = connection.getSalesCoverageStart() == null ? started.minusDays(90) : connection.getSalesCoverageStart();
        OffsetDateTime cursor = connection.getSalesSyncedAt() == null ? coverage : connection.getSalesSyncedAt().minusMinutes(5);
        JsonNode orders = client.getOrdersModifiedSince(storeId, cursor.toInstant().toEpochMilli()).path("elements");
        if (!orders.isArray()) throw new IllegalStateException("Invalid Clover orders response");
        int skipped = 0;
        for (JsonNode order : orders) {
            String id = order.path("id").asText("");
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalStateException("Invalid Clover order identifier");
            // Replace each changed order atomically: retries do not double-count, refunds remove prior sales.
            sales.deleteByStoreIdAndOrderId(storeId, id);
            sales.flush();
            long created = order.path("createdTime").asLong(0);
            if (!"PAID".equals(order.path("paymentState").asText()) || order.path("testMode").asBoolean(false)
                    || order.path("deletedTimestamp").asLong(0) > 0 || created <= 0) { skipped++; continue; }
            OffsetDateTime occurred = OffsetDateTime.ofInstant(Instant.ofEpochMilli(created), ZoneOffset.UTC);
            if (occurred.isBefore(coverage) || occurred.isAfter(started.plusMinutes(5))) { skipped++; continue; }
            JsonNode lines = client.getOrderLines(storeId, id).path("elements");
            if (!lines.isArray()) throw new IllegalStateException("Invalid Clover order lines response");
            for (JsonNode line : lines) {
                String lineId = line.path("id").asText("");
                String itemId = line.path("item").path("id").asText("");
                if (!lineId.matches("[A-Za-z0-9_-]{1,64}") || itemId.isBlank()
                        || line.path("refunded").asBoolean(false) || line.path("exchanged").asBoolean(false)
                        || line.path("isOrderFee").asBoolean(false)) { skipped++; continue; }
                var product = products.findByStoreIdAndCloverItemId(storeId, itemId);
                BigDecimal units = units(line);
                if (product.isEmpty() || units == null) { skipped++; continue; }
                sales.save(new SalesEvent(storeId, product.get().getId(), id, lineId, units, occurred));
            }
        }
        connection.markSalesSynchronized(started, coverage, skipped);
    }
    static BigDecimal units(JsonNode line) {
        BigDecimal value;
        if (line.path("quantitySold").isNumber()) value = line.path("quantitySold").decimalValue();
        else if (!line.path("unitName").asText("").isBlank()
                || "PER_UNIT".equals(line.path("item").path("priceType").asText())) {
            if (!line.path("unitQty").isNumber()) return null;
            value = line.path("unitQty").decimalValue().movePointLeft(3);
        } else value = BigDecimal.ONE; // Each ordinary line item represents one unit; unitQty is not a multiplier.
        try {
            value = value.setScale(3, RoundingMode.UNNECESSARY);
            return value.signum() > 0 && value.precision() <= 18 ? value : null;
        } catch (ArithmeticException ex) { return null; }
    }
}
