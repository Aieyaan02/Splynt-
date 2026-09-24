package com.aieyaan.splynt.account.dto;

public record StoreSummaryResponse(
        Long id,
        String name,
        String slug,
        String timezone,
        String currencyCode,
        String countryCode) {
}