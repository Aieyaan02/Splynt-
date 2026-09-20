package com.aieyaan.splynt.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(

        @NotBlank(message = "Barcode is required")
        @Size(max = 64, message = "Barcode cannot exceed 64 characters")
        String barcode,

        @NotBlank(message = "Product name is required")
        @Size(max = 150, message = "Product name cannot exceed 150 characters")
        String name,

        @Size(max = 120, message = "Brand cannot exceed 120 characters")
        String brand,

        @Size(max = 120, message = "Category cannot exceed 120 characters")
        String category,

        @NotNull(message = "Quantity is required")
        @PositiveOrZero(message = "Quantity cannot be negative")
        Integer quantity,

        @NotNull(message = "Reorder level is required")
        @PositiveOrZero(message = "Reorder level cannot be negative")
        Integer reorderLevel,

        @NotNull(message = "Target stock is required")
        @Positive(message = "Target stock must be greater than zero")
        Integer targetStock,

        @DecimalMin(
                value = "0.0",
                inclusive = true,
                message = "Unit cost cannot be negative")
        BigDecimal unitCost) {
}