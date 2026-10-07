package com.aieyaan.splynt.clover;

import java.util.HashMap;
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

        Map<String, Integer> quantities =
                extractStockQuantities(stocksResponse);

        int received = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;

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
                continue;
            }

            String barcode = textValue(itemNode, "code");

            if (barcode == null) {
                barcode = "CLOVER-" + cloverItemId;
            }

            if (barcode.length() > 64) { skipped++; continue; }

            // An absent stock record is unknown, never evidence of zero stock.
            Integer quantity = quantities.get(cloverItemId);
            if (quantity == null) {
                skipped++;
                continue;
            }

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
                continue;
            }

            if (existingProduct.isPresent()) {
                if (!existingProduct.get().isActive()) {
                    skipped++;
                    continue;
                }
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

            Product product = new Product(
                    store,
                    barcode,
                    name,
                    null,
                    "Clover",
                    quantity,
                    DEFAULT_REORDER_LEVEL,
                    Math.max(DEFAULT_TARGET_STOCK, quantity),
                    null,
                    ProductSource.CLOVER
            );

            product.setCloverItemId(cloverItemId);
            applyCatalogDetails(product, itemNode);
            productRepository.save(product);
            if (quantity > 0) {
                recordReconciliation(product, 0, quantity);
            }
            created++;
        }

        return new CloverSyncResponse(
                cloverClient.getMerchantId(storeId),
                received,
                created,
                updated,
                skipped,
                java.time.OffsetDateTime.now()
        );
    }

    private Map<String, Integer> extractStockQuantities(
            JsonNode stocksResponse) {

        Map<String, Integer> quantities = new HashMap<>();
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
            // Whole-unit inventory only: never silently round weighed goods or negative stock.
            java.math.BigDecimal amount = value.decimalValue();
            try {
                int quantity = amount.intValueExact();
                if (quantity >= 0) quantities.put(itemId, quantity);
            } catch (ArithmeticException unsupportedQuantity) {
                // Returned in the skipped count for the connection status.
            }
        }

        return quantities;
    }

    private void updateExistingProduct(
            Product product,
            String cloverItemId,
            String barcode,
            String name,
            int newQuantity) {

        product.setCloverItemId(cloverItemId);
        product.setBarcode(barcode);
        product.setName(name);
        product.setSource(ProductSource.CLOVER);

        synchronizeQuantity(product, newQuantity);

        productRepository.save(product);
    }

    private void synchronizeQuantity(
            Product product,
            int newQuantity) {

        int currentQuantity = product.getQuantity();
        int difference = newQuantity - currentQuantity;

        if (difference > 0) {
            product.restock(difference);
        } else if (difference < 0) {
            product.recordSale(Math.abs(difference));
        }
        if (difference != 0) {
            recordReconciliation(product, currentQuantity, newQuantity);
        }
    }

    private void recordReconciliation(Product product, int before, int after) {
        movements.save(new InventoryMovement(product, InventoryMovementType.ADJUSTMENT,
                after - before, before, after, InventoryMovementSource.CLOVER,
                "Stock reconciled from Clover", product.getCloverItemId()));
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