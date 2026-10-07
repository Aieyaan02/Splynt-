package com.aieyaan.splynt.store;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.product.*;

@SpringBootTest @Transactional
class StoreSettingsHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired ProductRepository products;
    MockMvc mvc;
    Store store;
    Organization organization;
    AppUser owner;
    OrganizationMembership membership;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        organization = organizations.saveAndFlush(new Organization("Retail", suffix));
        store = stores.saveAndFlush(new Store(organization, "First store", suffix));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        membership = memberships.saveAndFlush(new OrganizationMembership(organization, owner, MembershipRole.OWNER));
    }
    String path() { return "/api/stores/" + store.getId() + "/settings"; }
    String payload(long version, String timezone, String currency) {
        return "{\"version\":" + version + ",\"name\":\"Downtown\",\"city\":\"Chicago\",\"state\":\"Illinois\",\"countryCode\":\"US\",\"timezone\":\"" + timezone + "\",\"currencyCode\":\"" + currency + "\"}";
    }
    @Test void ownerCanSetLocationAndTimezoneAndAccountReflectsChanges() throws Exception {
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(store.getVersion(), "America/Chicago", "USD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.city").value("Chicago"))
                .andExpect(jsonPath("$.timezone").value("America/Chicago"));
        mvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.organizations[0].stores[0].name").value("Downtown"));
    }
    @Test void staleVersionCannotOverwriteSettings() throws Exception {
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(999, "America/Chicago", "USD")))
                .andExpect(status().isConflict());
        assertEquals("First store", store.getName());
    }
    @Test void invalidTimezoneAndCountryRejected() throws Exception {
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(store.getVersion(), "Mars/Olympus", "USD")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(store.getVersion(), "UTC", "USD").replace("\"US\"", "\"ZZ\"")))
                .andExpect(status().isBadRequest());
    }
    @Test void currencyCanBeConfiguredBeforeInventoryExists() throws Exception {
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(store.getVersion(), "America/Toronto", "CAD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currencyCode").value("CAD"));
    }
    @Test void existingInventoryCostsCannotBeRelabeledInAnotherCurrency() throws Exception {
        products.saveAndFlush(new Product(store, "ABC", "Coffee", null, null, 1, 1, 10, null, ProductSource.MANUAL));
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(store.getVersion(), "UTC", "EUR")))
                .andExpect(status().isBadRequest());
        assertEquals("USD", store.getCurrencyCode());
    }
    @Test void ownerCreatesIndependentStoreWithItsOwnRegionalSettings() throws Exception {
        mvc.perform(post("/api/organizations/" + organization.getId() + "/stores")
                .with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(0, "Europe/London", "GBP")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currencyCode").value("GBP"));
        assertEquals(2, stores.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organization.getId()).size());
        assertEquals("America/New_York", store.getTimezone());
    }
    @Test void employeeReadsButCannotEditOrCreateStores() throws Exception {
        membership.changeRole(MembershipRole.EMPLOYEE); memberships.saveAndFlush(membership);
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))).andExpect(status().isOk());
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(0, "UTC", "USD"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/organizations/" + organization.getId() + "/stores")
                .with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(0, "UTC", "USD"))).andExpect(status().isForbidden());
    }
    @Test void outsiderCannotReadEditOrCreateInAnotherOrganization() throws Exception {
        var outsider = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", "hash", "Other", "User"));
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject(outsider.getId().toString())))).andExpect(status().isForbidden());
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(outsider.getId().toString())))
                .contentType("application/json").content(payload(0, "UTC", "USD"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/organizations/" + organization.getId() + "/stores")
                .with(jwt().jwt(j -> j.subject(outsider.getId().toString())))
                .contentType("application/json").content(payload(0, "UTC", "USD"))).andExpect(status().isForbidden());
    }
    @Test void disabledAccountCannotReuseExistingTokenForStoreAccess() throws Exception {
        owner.disable(); users.saveAndFlush(owner);
        assertUnavailable();
    }
    @Test void inactiveOrganizationCannotBeAccessedWithExistingToken() throws Exception {
        organization.deactivate(); organizations.saveAndFlush(organization);
        assertUnavailable();
    }
    void assertUnavailable() throws Exception {
        for (String endpoint : new String[] { path(), "/api/stores/" + store.getId() + "/insights", "/api/stores/" + store.getId() + "/products" })
            mvc.perform(get(endpoint).with(jwt().jwt(j -> j.subject(owner.getId().toString())))).andExpect(status().isForbidden());
        mvc.perform(patch(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(0, "UTC", "USD"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/organizations/" + organization.getId() + "/stores")
                .with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(payload(0, "UTC", "USD"))).andExpect(status().isForbidden());
    }
}
