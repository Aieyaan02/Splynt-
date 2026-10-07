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
        String note,
        @jakarta.validation.constraints.Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", message = "Request ID must be a UUID")
        String requestId) {
    public InventoryChangeRequest(BigDecimal quantity, String note) { this(quantity, note, null); }
    public InventoryChangeRequest(int quantity, String note) {
        this(BigDecimal.valueOf(quantity), note, null);
    }
}
