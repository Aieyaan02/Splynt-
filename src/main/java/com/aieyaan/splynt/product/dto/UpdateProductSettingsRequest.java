package com.aieyaan.splynt.product.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record UpdateProductSettingsRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 120) String brand,
        @Size(max = 120) String category,
        @NotNull @PositiveOrZero Integer reorderLevel,
        @NotNull @Positive Integer targetStock,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {}
