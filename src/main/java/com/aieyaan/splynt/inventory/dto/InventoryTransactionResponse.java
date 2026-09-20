package com.aieyaan.splynt.inventory.dto;

import com.aieyaan.splynt.product.dto.ProductResponse;

public record InventoryTransactionResponse(
        ProductResponse product,
        InventoryMovementResponse movement) {
}