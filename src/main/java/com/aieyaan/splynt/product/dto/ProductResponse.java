package com.aieyaan.splynt.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.product.ProductSource;

public record ProductResponse(
        Long id,
        long version,
        Long storeId,
        String barcode,
        String name,
        String brand,
        String category,
        int quantity,
        int reorderLevel,
        int targetStock,
        BigDecimal unitCost,
        String cloverItemId,
        ProductSource source,
        boolean active,
        boolean lowStock,
        int suggestedReorderQuantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        com.aieyaan.splynt.product.CloverCatalogDetails cloverDetails) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getVersion(),
                product.getStoreId(),
                product.getBarcode(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getQuantity(),
                product.getReorderLevel(),
                product.getTargetStock(),
                product.getUnitCost(),
                product.getCloverItemId(),
                product.getSource(),
                product.isActive(),
                product.isLowStock(),
                product.calculateBaseReorderQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getCloverDetails()
        );
    }
}