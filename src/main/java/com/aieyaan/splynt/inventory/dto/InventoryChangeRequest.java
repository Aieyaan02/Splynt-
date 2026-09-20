package com.aieyaan.splynt.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InventoryChangeRequest(

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note) {
}