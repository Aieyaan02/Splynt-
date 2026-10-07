package com.aieyaan.splynt.clover;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.clover.dto.*;
import com.aieyaan.splynt.insights.CloverSalesSyncService;

@SpringBootTest @Transactional
class CloverImportReviewHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired CloverSyncJobs jobs;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean CloverInventorySyncService sync;
    @MockitoBean CloverSalesSyncService sales;
    MockMvc mvc; Store store; AppUser owner;
    OffsetDateTime completed;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        var org = organizations.saveAndFlush(new Organization("Shop", suffix));
        store = stores.saveAndFlush(new Store(org, "Shop", suffix));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        memberships.saveAndFlush(new OrganizationMembership(org, owner, MembershipRole.OWNER));
        var credential = new CloverOAuthCredential(suffix, "private-access-token", "private-refresh-token", null, null);
        credential.assignStore(store.getId()); credentials.saveAndFlush(credential);
        completed = OffsetDateTime.now().minusMinutes(1);
        when(sync.synchronize(store.getId())).thenReturn(result(completed, true));
    }
    CloverSyncResponse result(OffsetDateTime at, boolean issue) {
        return new CloverSyncResponse("merchant", 1, 0, 1, issue ? 1 : 0, at, issue ? 1 : 0,
                issue ? List.of(new CloverImportIssue("item1", "Coffee", "ABC", "UNKNOWN_STOCK", "Check stock tracking in Clover.")) : List.of());
    }
    String path() { return "/api/stores/" + store.getId() + "/integrations/clover"; }
    @Test void completedImportReviewPersistsAndApiDoesNotExposeCredentials() throws Exception {
        jobs.run(store.getId()); entityManager.flush(); entityManager.clear();
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issueCount").value(1))
                .andExpect(jsonPath("$.issues[0].name").value("Coffee"))
                .andExpect(jsonPath("$.issues[0].nextStep").value("Check stock tracking in Clover."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-"))));
    }
    @Test void successfulFixClearsThePreviousIssueList() {
        jobs.run(store.getId());
        when(sync.synchronize(store.getId())).thenReturn(result(completed.plusSeconds(1), false));
        jobs.run(store.getId());
        var connection = credentials.findByStoreId(store.getId()).orElseThrow();
        assertEquals(0, connection.getInventoryIssueCount()); assertTrue(connection.getInventoryIssues().isEmpty());
        assertNull(connection.getLastSyncError());
    }
    @Test void failedRefreshRetainsTheLastCompletedReview() {
        jobs.run(store.getId());
        when(sync.synchronize(store.getId())).thenThrow(new IllegalStateException("Provider failed"));
        assertThrows(IllegalStateException.class, () -> jobs.run(store.getId()));
        var connection = credentials.findByStoreId(store.getId()).orElseThrow();
        assertEquals(1, connection.getInventoryIssueCount());
        assertEquals("Coffee", connection.getInventoryIssues().getFirst().name());
        assertTrue(connection.getLastSyncError().contains("could not be refreshed"));
    }
    @Test void lateStatusWriteCannotReplaceANewerCompletedReview() {
        jobs.run(store.getId());
        when(sync.synchronize(store.getId())).thenReturn(result(completed.minusSeconds(1), false));
        jobs.run(store.getId());
        assertEquals(1, credentials.findByStoreId(store.getId()).orElseThrow().getInventoryIssueCount());
    }
    @Test void outsiderCannotReadStoreImportDetails() throws Exception {
        jobs.run(store.getId());
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject("999999")))).andExpect(status().isForbidden());
    }
    @Test void manualSyncFailureDoesNotExposeInternalDiagnostics() throws Exception {
        when(sync.synchronize(store.getId())).thenThrow(new IllegalArgumentException("private SQL and provider token"));
        mvc.perform(post(path() + "/sync").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Inventory could not be refreshed. Retry, or reconnect Clover if access expired."));
    }

}
