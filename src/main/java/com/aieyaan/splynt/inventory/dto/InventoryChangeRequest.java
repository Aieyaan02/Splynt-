package com.aieyaan.splynt.inventory.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InventoryChangeRequest(

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        @jakarta.validation.constraints.Digits(integer = 12, fraction = 6) BigDecimal quantity,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note) {
    public InventoryChangeRequest(int quantity, String note) { this(BigDecimal.valueOf(quantity), note); }
}