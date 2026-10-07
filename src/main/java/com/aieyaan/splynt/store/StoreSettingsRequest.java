package com.aieyaan.splynt.store;

import jakarta.validation.constraints.*;

public record StoreSettingsRequest(
        @PositiveOrZero Long version,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode) {}
