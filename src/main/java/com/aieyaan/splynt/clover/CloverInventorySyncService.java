package com.aieyaan.splynt.clover;

import java.util.HashMap;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.clover.dto.CloverSyncResponse;
import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.inventory.*;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.product.ProductSource;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;
import com.aieyaan.splynt.tenant.exception.StoreNotFoundException;
import tools.jackson.databind.JsonNode;

@Service
public class CloverInventorySyncService {

    private static final int DEFAULT_REORDER_LEVEL = 5;
    private static final int DEFAULT_TARGET_STOCK = 20;

    private final CloverInventoryClient cloverClient;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final InventoryMovementRepository movements;

    public CloverInventorySyncService(
            CloverInventoryClient cloverClient,
            ProductRepository productRepository,
            StoreRepository storeRepository,
            InventoryMovementRepository movements) {

        this.cloverClient = cloverClient;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.movements = movements;
    }

    @Transactional
    public CloverSyncResponse synchronize(Long storeId) {
        Store store = storeRepository.findLockedById(storeId)
                .filter(Store::isActive)
                .orElseThrow(() -> new StoreNotFoundException(
                        "Active store with ID "
                                + storeId
                                + " was not found"
                ));

        JsonNode itemsResponse = cloverClient.getItems(storeId);
        JsonNode stocksResponse = cloverClient.getItemStocks(storeId);

        Map<String, BigDecimal> quantities =
                extractStockQuantities(stocksResponse);

        int received = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        var review = new ImportReview();

        JsonNode items = itemsResponse.path("elements");

        if (!items.isArray()) {
            throw new IllegalStateException(
                    "Clover returned an invalid inventory response"
            );
        }

        for (JsonNode itemNode : items) {
            received++;

            if (itemNode.path("deleted").asBoolean(false) || itemNode.path("deletedTime").asLong(0) > 0) {
                skipped++;
                continue;
            }

            String cloverItemId = textValue(itemNode, "id");
            String name = textValue(itemNode, "name");

            if (cloverItemId == null || cloverItemId.length() > 64 || name == null || name.length() > 150) {
                skipped++;
                review.add(itemNode, "INVALID_ITEM", "Check the Clover item ID and name. Names must be present and at most 150 characters.");
                continue;
            }

            String barcode = textValue(itemNode, "code");

            if (barcode == null) {
                barcode = "CLOVER-" + cloverItemId;
            }

            if (barcode.length() > 64) { skipped++; review.add(itemNode, "INVALID_BARCODE", "Use a barcode of at most 64 characters in Clover, then sync again."); continue; }

            // An absent stock record is unknown, never evidence of zero stock.
            BigDecimal quantity = quantities.get(cloverItemId);
            Optional<Product> existingProduct =
                    productRepository.findByStoreIdAndCloverItemId(
                            storeId,
                            cloverItemId
                    );

            // Barcode equality is not proof of identity. Never convert a manual product or
            // relink another Clover item just because its barcode matches this item.
            var barcodeOwner = productRepository.findByStoreIdAndBarcode(storeId, barcode);
            if (barcodeOwner.isPresent() && (existingProduct.isEmpty()
                    || !java.util.Objects.equals(barcodeOwner.get().getCloverItemId(), cloverItemId))) {
                skipped++;
                review.add(itemNode, "BARCODE_CONFLICT", "Give distinct products unique barcodes in Clover. If both entries represent the same product, contact support before merging; Splynt never merges automatically.");
                continue;
            }

            if (existingProduct.isPresent()) {
                if (!existingProduct.get().isActive()) {
                    skipped++;
                    continue;
                }
                if (quantity == null) { skipped++; review.unknownStock(itemNode); }
                updateExistingProduct(
                        existingProduct.get(),
                        cloverItemId,
                        barcode,
                        name,
                        quantity
                );

                applyCatalogDetails(existingProduct.get(), itemNode);
                updated++;
                continue;
            }

            if (quantity == null) { skipped++; review.unknownStock(itemNode); }
            Product product = new Product(
                    store,
                    barcode,
                    name,
                    null,
                    "Clover",
                    quantity == null ? BigDecimal.ZERO : quantity,
                    BigDecimal.valueOf(DEFAULT_REORDER_LEVEL),
                    quantity == null ? BigDecimal.valueOf(DEFAULT_TARGET_STOCK) : BigDecimal.valueOf(DEFAULT_TARGET_STOCK).max(quantity),
                    null,
                    ProductSource.CLOVER
            );

            if (quantity == null) product.markStockUnknown();
            product.setCloverItemId(cloverItemId);
            applyCatalogDetails(product, itemNode);
            productRepository.save(product);
            if (quantity != null && quantity.signum() != 0) {
                recordReconciliation(product, BigDecimal.ZERO, quantity, true);
            }
            created++;
        }

        return new CloverSyncResponse(
                cloverClient.getMerchantId(storeId),
                received,
                created,
                updated,
                skipped,
                java.time.OffsetDateTime.now(), review.count, java.util.List.copyOf(review.issues)
        );
    }

    private static class ImportReview {
        int count;
        final java.util.List<com.aieyaan.splynt.clover.dto.CloverImportIssue> issues = new java.util.ArrayList<>();
        void unknownStock(JsonNode item) {
            add(item, "UNKNOWN_STOCK", "Check stock tracking and the balance in Clover, then sync again. Splynt supports up to six decimal places and balances below one trillion in magnitude; missing or unsupported stock stays unknown.");
        }
        void add(JsonNode item, String reason, String nextStep) {
            count++;
            if (issues.size() < 100) issues.add(new com.aieyaan.splynt.clover.dto.CloverImportIssue(
                    bounded(item, "id", 64), bounded(item, "name", 150), bounded(item, "code", 64), reason, nextStep));
        }
        private String bounded(JsonNode item, String field, int max) {
            String value = item.path(field).asText("").trim();
            return value.isEmpty() ? null : value.substring(0, Math.min(value.length(), max));
        }
    }

    private Map<String, BigDecimal> extractStockQuantities(
            JsonNode stocksResponse) {

        Map<String, BigDecimal> quantities = new HashMap<>();
        JsonNode stocks = stocksResponse.path("elements");

        if (!stocks.isArray()) {
            throw new IllegalStateException("Clover returned an invalid stock response");
        }

        for (JsonNode stockNode : stocks) {
            String itemId = stockNode
                    .path("item")
                    .path("id")
                    .asText(null);

            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            JsonNode value = stockNode.get("quantity");
            if (value == null || !value.isNumber()) {
                continue;
            }
            try {
                quantities.put(itemId, Product.stockAmount(value.decimalValue()));
            } catch (IllegalArgumentException unsupportedQuantity) {
                // Preserve the catalog entry while marking stock unknown; never round a provider value.
            }
        }

        return quantities;
    }

    private void updateExistingProduct(
            Product product,
            String cloverItemId,
            String barcode,
            String name,
            BigDecimal newQuantity) {

        product.setCloverItemId(cloverItemId);
        product.setBarcode(barcode);
        product.setName(name);
        product.setSource(ProductSource.CLOVER);

        synchronizeQuantity(product, newQuantity);

        productRepository.save(product);
    }

    private void synchronizeQuantity(
            Product product,
            BigDecimal newQuantity) {

        if (newQuantity == null) { product.markStockUnknown(); return; }
        BigDecimal currentQuantity = product.getQuantity();
        boolean wasKnown = product.isStockKnown();
        product.reconcileStock(newQuantity);
        if (newQuantity.compareTo(currentQuantity) != 0) recordReconciliation(product, currentQuantity, newQuantity, wasKnown);
    }

    private void recordReconciliation(Product product, BigDecimal before, BigDecimal after, boolean wasKnown) {
        movements.save(new InventoryMovement(product, InventoryMovementType.ADJUSTMENT,
                after.subtract(before), before, after, InventoryMovementSource.CLOVER,
                wasKnown ? "Stock reconciled from Clover" : "Stock restored from Clover; prior balance was last known, not current",
                product.getCloverItemId()));
    }

    private void applyCatalogDetails(Product product, JsonNode item) {
        java.util.List<String> categories = new java.util.ArrayList<>();
        JsonNode categoryNodes = item.path("categories").path("elements");
        if (categoryNodes.isArray()) {
            for (JsonNode category : categoryNodes) {
                String name = textValue(category, "name");
                if (name != null && name.length() <= 120 && !categories.contains(name)) categories.add(name);
            }
            categories.sort(String.CASE_INSENSITIVE_ORDER);
            product.setCategory(categories.isEmpty() ? null : categories.getFirst());
        }
        product.setCloverDetails(new com.aieyaan.splynt.product.CloverCatalogDetails(
                boundedText(item, "sku", 127), boundedText(item, "alternateName", 255),
                boundedText(item, "unitName", 64), boundedText(item, "priceType", 32),
                item.path("available").isBoolean() ? item.path("available").asBoolean() : null,
                item.path("hidden").isBoolean() ? item.path("hidden").asBoolean() : null,
                categoryNodes.isArray() ? java.util.List.copyOf(categories) : null));
    }

    private String boundedText(JsonNode item, String field, int max) {
        String value = textValue(item, field);
        return value != null && value.length() <= max ? value : null;
    }

    private String textValue(
            JsonNode node,
            String fieldName) {

        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}