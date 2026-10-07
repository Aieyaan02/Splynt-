package com.aieyaan.splynt.product;

import java.util.List;

/** Provider-owned catalog fields; local cost/reorder settings remain independent. */
public record CloverCatalogDetails(String sku, String alternateName, String unitName, String priceType,
        Boolean available, Boolean hidden, List<String> categories, CloverMoney money) {
    public CloverCatalogDetails(String sku, String alternateName, String unitName, String priceType,
            Boolean available, Boolean hidden, List<String> categories) {
        this(sku, alternateName, unitName, priceType, available, hidden, categories, null);
    }
    /** Exact decimal strings avoid loss of precision in browser clients. No currency conversion. */
    public record CloverMoney(String currency, String price, String cost, Boolean matchesStoreCurrency) {}
}
