package com.aieyaan.splynt.store;

import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.OptimisticLockingFailureException;
import com.aieyaan.splynt.tenant.*;

@Service
@Transactional
public class StoreSettingsService {
    private final StoreRepository stores;
    private final OrganizationMembershipRepository memberships;
    private final AppUserRepository users;
    private final com.aieyaan.splynt.product.ProductRepository products;
    private final com.aieyaan.splynt.clover.CloverOAuthCredentialRepository connections;
    public StoreSettingsService(StoreRepository stores, OrganizationMembershipRepository memberships, AppUserRepository users, com.aieyaan.splynt.product.ProductRepository products,
            com.aieyaan.splynt.clover.CloverOAuthCredentialRepository connections) {
        this.stores = stores; this.memberships = memberships; this.users = users; this.products = products; this.connections = connections;
    }
    @Transactional(readOnly = true)
    public StoreSettingsResponse read(Long storeId) {
        return StoreSettingsResponse.from(stores.findById(storeId).orElseThrow());
    }
    public StoreSettingsResponse update(Long storeId, StoreSettingsRequest request) {
        Store store = stores.findLockedById(storeId).filter(Store::isActive).orElseThrow();
        if (request.version() == null) throw new IllegalArgumentException("Store version is required. Reload settings and retry.");
        if (request.version() != store.getVersion()) throw new OptimisticLockingFailureException("Store settings changed. Reload settings before saving.");
        apply(store, request);
        return StoreSettingsResponse.from(stores.saveAndFlush(store));
    }
    public StoreSettingsResponse create(Long organizationId, Long userId, StoreSettingsRequest request) {
        if (users.findById(userId).filter(AppUser::isEnabled).isEmpty()) throw new AccessDeniedException("Account is unavailable");
        var membership = memberships.findByOrganizationIdAndUserIdAndActiveTrue(organizationId, userId)
                .filter(m -> m.getOrganization().isActive())
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Only organization owners and admins can add stores"));
        Store store = new Store(membership.getOrganization(), request.name(), "store-" + UUID.randomUUID());
        apply(store, request);
        return StoreSettingsResponse.from(stores.saveAndFlush(store));
    }
    private void apply(Store store, StoreSettingsRequest request) {
        if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) throw new IllegalArgumentException("Choose a valid IANA timezone");
        if (!Arrays.asList(Locale.getISOCountries()).contains(request.countryCode())) throw new IllegalArgumentException("Choose a valid country code");
        try { Currency.getInstance(request.currencyCode()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Choose a valid currency code"); }
        if (!store.getCurrencyCode().equals(request.currencyCode()) && store.getId() != null
                && (products.existsByStoreId(store.getId()) || connections.findByStoreId(store.getId()).isPresent())) {
            // Existing costs are denominated in the original currency; never silently relabel them.
            throw new IllegalArgumentException("Currency cannot be changed after adding products or connecting Clover. Existing costs cannot be relabeled as another currency.");
        }
        store.updateDetails(request.name(), store.getSlug());
        store.updateAddress(store.getAddressLine1(), store.getAddressLine2(), request.city(), request.state(), store.getPostalCode(), request.countryCode());
        store.updateRegionalSettings(request.timezone(), request.currencyCode());
    }
}
