package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.math.BigDecimal;
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
class DecimalInventoryHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired ProductRepository products;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc; Store store; AppUser owner; Product product;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        var organization = organizations.saveAndFlush(new Organization("Shop", suffix));
        store = stores.saveAndFlush(new Store(organization, "Shop", suffix));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        memberships.saveAndFlush(new OrganizationMembership(organization, owner, MembershipRole.OWNER));
        product = products.saveAndFlush(new Product(store, "WEIGHT", "Loose tea", null, null,
                new BigDecimal("0.333333"), new BigDecimal("0.3"), new BigDecimal("2.5"), null, ProductSource.MANUAL));
    }
    String path() { return "/api/stores/" + store.getId() + "/products"; }
    @Test void fractionalSaleAndHistoryPersistWithoutRounding() throws Exception {
        mvc.perform(post(path() + "/" + product.getId() + "/inventory/sales").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType("application/json").content("{\"quantity\":0.1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.product.quantity").value(0.233333))
                .andExpect(jsonPath("$.product.lowStock").value(true))
                .andExpect(jsonPath("$.product.suggestedReorderQuantity").value(2.266667))
                .andExpect(jsonPath("$.movement.quantityChange").value(-0.1));
        Long id = product.getId(); entityManager.flush(); entityManager.clear();
        assertEquals(0, products.findById(id).orElseThrow().getQuantity().compareTo(new BigDecimal("0.233333")));
    }
    @Test void excessivePrecisionAndOverflowAreRejectedBeforeMutation() throws Exception {
        for (String amount : new String[] {"0.0000001", "1000000000000"})
            mvc.perform(post(path() + "/" + product.getId() + "/inventory/restocks").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                    .contentType("application/json").content("{\"quantity\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        assertEquals(0, product.getQuantity().compareTo(new BigDecimal("0.333333")));
    }
    @Test void unknownStockIsNullInApiAndExcludedFromLowStockList() throws Exception {
        product.setSource(ProductSource.CLOVER); product.markStockUnknown(); products.saveAndFlush(product);
        mvc.perform(get(path() + "/" + product.getId()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").isEmpty())
                .andExpect(jsonPath("$.stockKnown").value(false)).andExpect(jsonPath("$.lowStock").value(false));
        assertTrue(products.findLowStockProductsByStoreId(store.getId()).isEmpty());
    }
    @Test void negativeCloverBalanceIsPreservedAndNeedsRestocking() {
        product.setSource(ProductSource.CLOVER); product.reconcileStock(new BigDecimal("-1.25")); products.saveAndFlush(product);
        assertEquals(1, products.findLowStockProductsByStoreId(store.getId()).size());
        assertEquals(0, product.calculateBaseReorderQuantity().compareTo(new BigDecimal("3.75")));
    }
    @Test void knownStockCanBeRestoredWithoutChangingThresholds() {
        product.markStockUnknown(); product.reconcileStock(new BigDecimal("1.234567")); products.saveAndFlush(product);
        assertTrue(product.isStockKnown()); assertFalse(product.isLowStock());
        assertEquals(0, product.getReorderLevel().compareTo(new BigDecimal("0.3")));
    }
}
