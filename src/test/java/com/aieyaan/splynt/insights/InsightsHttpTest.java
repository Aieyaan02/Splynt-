package com.aieyaan.splynt.insights;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.product.*;
import com.aieyaan.splynt.clover.*;

@SpringBootTest @Transactional
class InsightsHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired ProductRepository products;
    @Autowired SalesEventRepository sales;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired InsightsService insights;
    MockMvc mvc;
    Store store;
    Store other;
    AppUser owner;
    ZoneId zone = ZoneId.of("America/New_York");
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        var org = organizations.saveAndFlush(new Organization("Retail", suffix));
        store = stores.saveAndFlush(new Store(org, "Our store", suffix));
        var otherOrg = organizations.saveAndFlush(new Organization("Other", UUID.randomUUID().toString()));
        other = stores.saveAndFlush(new Store(otherOrg, "Other store", "other"));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        memberships.saveAndFlush(new OrganizationMembership(org, owner, MembershipRole.OWNER));
        for (Store target : List.of(store, other)) {
            var product = products.saveAndFlush(new Product(target, "ABC", "Coffee", null, null, 20, 5, 30, null, ProductSource.CLOVER));
            var credential = new CloverOAuthCredential(UUID.randomUUID().toString(), "unused", "unused", null, null);
            credential.assignStore(target.getId());
            credential.markSalesSynchronized(OffsetDateTime.now(), OffsetDateTime.now().minusYears(3), 0);
            credentials.saveAndFlush(credential);
            var at = YearMonth.now(zone).minusMonths(1).atDay(15).atTime(12, 0).atZone(zone).toOffsetDateTime();
            sales.saveAndFlush(new SalesEvent(target.getId(), product.getId(), "order", "line", target == store ? BigDecimal.TEN : BigDecimal.valueOf(999), at));
            // A future event must never enter either window or historical totals.
            sales.saveAndFlush(new SalesEvent(target.getId(), product.getId(), "future", "line", BigDecimal.valueOf(99999), OffsetDateTime.now().plusDays(2)));
        }
    }
    @Test void endpointUsesOnlyTheRequestedStoresHistoricalSales() throws Exception {
        mvc.perform(get("/api/stores/" + store.getId() + "/insights").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.storeName").value("Our store"))
                .andExpect(jsonPath("$.history.metric").value("ORDERS"))
                .andExpect(jsonPath("$.history.months[23].orders").value(1));
        var source = insights.read(store.getId());
        assertEquals(1, source.history().months().stream().map(HistoricalSalesAnalysis.Month::orders)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
    }
    @Test void ownerCannotReadAnotherOrganizationsInsights() throws Exception {
        mvc.perform(get("/api/stores/" + other.getId() + "/insights").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isForbidden());
    }
}
