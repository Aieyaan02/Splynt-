package com.aieyaan.splynt.store;
import com.aieyaan.splynt.tenant.Store;

public record StoreSettingsResponse(Long id, long version, String name, String city, String state,
        String countryCode, String timezone, String currencyCode) {
    static StoreSettingsResponse from(Store store) {
        return new StoreSettingsResponse(store.getId(), store.getVersion(), store.getName(), store.getCity(),
                store.getState(), store.getCountryCode(), store.getTimezone(), store.getCurrencyCode());
    }
}
