package com.aieyaan.splynt.product;

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

@SpringBootTest @Transactional
class ProductSettingsHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ProductRepository products;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    MockMvc mvc;
    Store store;
    AppUser owner;
    Product product;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        var organization = organizations.saveAndFlush(new Organization("Store", suffix));
        store = stores.saveAndFlush(new Store(organization, "Store", suffix));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        memberships.saveAndFlush(new OrganizationMembership(organization, owner, MembershipRole.OWNER));
        product = products.saveAndFlush(new Product(store, "ABC", "Coffee", "Brand", "Pantry", 4, 2, 10, null, ProductSource.MANUAL));
    }
    String path() { return "/api/stores/" + store.getId() + "/products/" + product.getId(); }
    String settings(long version, int reorder, int target) {
        return "{\"version\":" + version + ",\"name\":\"New coffee\",\"brand\":\"Brand\",\"category\":\"Pantry\",\"reorderLevel\":" + reorder + ",\"targetStock\":" + target + ",\"unitCost\":2.50}";
    }
    @Test void cloverMetadataSurvivesPersistenceAndIsVisibleThroughTheProductApi() throws Exception {
        product.setSource(ProductSource.CLOVER);
        product.setCloverDetails(new CloverCatalogDetails("COFFEE-1", "Morning coffee", "cup", "FIXED", true, false,
                java.util.List.of("Drinks", "Breakfast")));
        products.saveAndFlush(product);
        String endpoint = path();
        String subject = owner.getId().toString();
        entityManager.clear();
        mvc.perform(get(endpoint).with(jwt().jwt(j -> j.subject(subject))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cloverDetails.sku").value("COFFEE-1"))
                .andExpect(jsonPath("$.cloverDetails.categories[1]").value("Breakfast"))
                .andExpect(jsonPath("$.cloverDetails.available").value(true))
                .andExpect(jsonPath("$.cloverDetails.hidden").value(false));
    }

    @Test void updatesTargetsAndRecalculatesLowStockWithoutChangingQuantity() throws Exception {
        mvc.perform(patch(path() + "/settings").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(settings(product.getVersion(), 5, 20)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.name").value("New coffee"))
                .andExpect(jsonPath("$.lowStock").value(true))
                .andExpect(jsonPath("$.suggestedReorderQuantity").value(16));
    }
    @Test void staleSettingsCannotOverwriteMoreRecentChanges() throws Exception {
        mvc.perform(patch(path() + "/settings").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(settings(999L, 5, 20)))
                .andExpect(status().isConflict());
        assertEquals(2, product.getReorderLevel());
    }
    @Test void rejectsInvalidTargets() throws Exception {
        mvc.perform(patch(path() + "/settings").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(settings(product.getVersion(), 10, 5)))
                .andExpect(status().isBadRequest());
    }
    @Test void employeeCanReadHistoryButCannotChangeTargets() throws Exception {
        var employee = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", "hash", "Employee", "One"));
        memberships.saveAndFlush(new OrganizationMembership(store.getOrganization(), employee, MembershipRole.EMPLOYEE));
        mvc.perform(patch(path() + "/settings").with(jwt().jwt(j -> j.subject(employee.getId().toString())))
                .contentType("application/json").content(settings(product.getVersion(), 5, 20)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path() + "/inventory/movements").with(jwt().jwt(j -> j.subject(employee.getId().toString()))))
                .andExpect(status().isOk());
    }
    @Test void outsiderCannotReadHistoryOrChangeSettings() throws Exception {
        mvc.perform(get(path() + "/inventory/movements").with(jwt().jwt(j -> j.subject("999999"))))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path() + "/settings").with(jwt().jwt(j -> j.subject("999999")))
                .contentType("application/json").content(settings(product.getVersion(), 5, 20)))
                .andExpect(status().isForbidden());
    }
    @Test void archivePreservesReadableHistory() throws Exception {
        product.setActive(false); products.saveAndFlush(product);
        mvc.perform(get(path() + "/inventory/movements").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk());
    }
    @Test void cloverOwnsIdentityAndQuantityWhileTargetsRemainEditable() throws Exception {
        product.setSource(ProductSource.CLOVER); products.saveAndFlush(product);
        mvc.perform(patch(path() + "/settings").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content(settings(product.getVersion(), 5, 20)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Coffee"))
                .andExpect(jsonPath("$.reorderLevel").value(5));
        for (String operation : new String[] {"sales", "restocks"})
            mvc.perform(post(path() + "/inventory/" + operation).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                    .contentType("application/json").content("{\"quantity\":1}"))
                    .andExpect(status().isBadRequest());
        assertEquals(4, product.getQuantity());
    }
}
