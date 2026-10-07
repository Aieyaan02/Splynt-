package com.aieyaan.splynt.inventory.dto;

import java.time.OffsetDateTime;

import com.aieyaan.splynt.inventory.InventoryMovement;
import com.aieyaan.splynt.inventory.InventoryMovementSource;
import com.aieyaan.splynt.inventory.InventoryMovementType;

public record InventoryMovementResponse(
        Long id,
        Long productId,
        InventoryMovementType movementType,
        java.math.BigDecimal quantityChange,
        java.math.BigDecimal quantityBefore,
        java.math.BigDecimal quantityAfter,
        InventoryMovementSource source,
        String note,
        String externalReference,
        OffsetDateTime createdAt) {

    public static InventoryMovementResponse from(
            InventoryMovement movement) {

        return new InventoryMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getMovementType(),
                movement.getQuantityChange(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getSource(),
                movement.getNote(),
                movement.getExternalReference(),
                movement.getCreatedAt());
    }
}