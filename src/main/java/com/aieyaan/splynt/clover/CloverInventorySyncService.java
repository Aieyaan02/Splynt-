package com.aieyaan.splynt.clover;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.clover.dto.CloverSyncResponse;
import com.aieyaan.splynt.product.Product;
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

    public CloverInventorySyncService(
            CloverInventoryClient cloverClient,
            ProductRepository productRepository,
            StoreRepository storeRepository) {

        this.cloverClient = cloverClient;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public CloverSyncResponse synchronize(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .filter(Store::isActive)
                .orElseThrow(() -> new StoreNotFoundException(
                        "Active store with ID "
                                + storeId
                                + " was not found"
                ));

        JsonNode itemsResponse = cloverClient.getItems();
        JsonNode stocksResponse = cloverClient.getItemStocks();

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

            if (itemNode.path("deleted").asBoolean(false)) {
                skipped++;
                continue;
            }

            String cloverItemId = textValue(itemNode, "id");
            String name = textValue(itemNode, "name");

            if (cloverItemId == null || name == null) {
                skipped++;
                continue;
            }

            String barcode = textValue(itemNode, "code");

            if (barcode == null) {
                barcode = "CLOVER-" + cloverItemId;
            }

            int quantity = quantities.getOrDefault(
                    cloverItemId,
                    0
            );

            Optional<Product> existingProduct =
                    productRepository.findByStoreIdAndCloverItemId(
                            storeId,
                            cloverItemId
                    );

            if (existingProduct.isEmpty()) {
                existingProduct =
                        productRepository.findByStoreIdAndBarcode(
                                storeId,
                                barcode
                        );
            }

            if (existingProduct.isPresent()) {
                updateExistingProduct(
                        existingProduct.get(),
                        cloverItemId,
                        barcode,
                        name,
                        quantity
                );

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
            productRepository.save(product);
            created++;
        }

        return new CloverSyncResponse(
                cloverClient.getMerchantId(),
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
            return quantities;
        }

        for (JsonNode stockNode : stocks) {
            String itemId = stockNode
                    .path("item")
                    .path("id")
                    .asText(null);

            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            int quantity = Math.max(
                    stockNode.path("quantity").asInt(0),
                    0
            );

            quantities.put(itemId, quantity);
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
        product.setActive(true);

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