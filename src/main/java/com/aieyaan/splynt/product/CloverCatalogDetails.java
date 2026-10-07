package com.aieyaan.splynt.product;

import java.util.List;

/** Provider-owned catalog fields; local cost/reorder settings remain independent. */
public record CloverCatalogDetails(String sku, String alternateName, String unitName, String priceType,
        Boolean available, Boolean hidden, List<String> categories) {}
