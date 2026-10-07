package com.aieyaan.splynt.advice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.aieyaan.splynt.insights.InsightsService;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest @Transactional
class AdviceHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired AdviceReportRepository reports;
    @MockitoBean OpenAiAdviceClient client;
    @MockitoBean InsightsService insights;
    MockMvc mvc;
    Store store;
    AppUser owner;
    OrganizationMembership membership;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        var org = organizations.saveAndFlush(new Organization("Retail", suffix));
        store = stores.saveAndFlush(new Store(org, "Store", suffix));
        owner = users.saveAndFlush(new AppUser(suffix + "@example.com", "hash", "Owner", "One"));
        membership = memberships.saveAndFlush(new OrganizationMembership(org, owner, MembershipRole.OWNER));
        when(client.configured()).thenReturn(true); when(client.model()).thenReturn("test-model");
        when(client.generate(anyMap())).thenReturn(OpenAiAdviceClientTest.content());
        when(insights.read(store.getId())).thenReturn(source(true, OffsetDateTime.now()));
    }
    String path() { return "/api/stores/" + store.getId() + "/advice"; }
    InsightsService.Insights source(boolean enough, OffsetDateTime synced) {
        return new InsightsService.Insights("Store", "Chicago, US", "America/Chicago", synced, null,
                LocalDate.now().minusDays(30), LocalDate.now(), new InsightsService.Summary(30, enough ? 35 : 5, BigDecimal.valueOf(70), enough,
                List.of(new InsightsService.ProductInsight(1L, "Coffee", BigDecimal.valueOf(70), BigDecimal.valueOf(2.33), BigDecimal.TEN, BigDecimal.valueOf(23))),
                Collections.nCopies(24, BigDecimal.ZERO), Collections.nCopies(7, BigDecimal.ZERO)), "Order creation times", "Not enough history for seasonality", com.aieyaan.splynt.insights.HistoricalSalesAnalysis.calculate(List.of(), Map.of(), ZoneId.of("America/Chicago"), null, LocalDate.now()));
    }
    @Test void ownerGeneratesSavedReportButRepeatedRequestsUseCooldown() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.summary").value("Coffee is a leading seller."))
                .andExpect(jsonPath("$.canGenerate").value(false));
        verify(client, times(1)).generate(anyMap());
        assertNotNull(reports.findById(store.getId()).orElseThrow().evidenceJson);
    }
    @Test void readNeverTriggersProviderCalls() throws Exception {
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report").isEmpty());
        verify(client, never()).generate(anyMap());
    }
    @Test void employeeCannotTriggerPaidGenerationButCanReadReports() throws Exception {
        membership.changeRole(MembershipRole.EMPLOYEE); memberships.saveAndFlush(membership);
        mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))).andExpect(status().isForbidden());
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))).andExpect(status().isOk());
        verify(client, never()).generate(anyMap());
    }
    @Test void outsiderCannotReadOrGenerateStoreAdvice() throws Exception {
        mvc.perform(get(path()).with(jwt().jwt(j -> j.subject("999999")))).andExpect(status().isForbidden());
        mvc.perform(post(path()).with(jwt().jwt(j -> j.subject("999999")))).andExpect(status().isForbidden());
        verify(client, never()).generate(anyMap());
    }
    @Test void providerFailureIsSanitizedAndDoesNotPermitImmediateRetry() throws Exception {
        when(client.generate(anyMap())).thenThrow(new IllegalStateException("secret provider response"));
        for (int i = 0; i < 2; i++) mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report").isEmpty())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.canGenerate").value(false));
        verify(client, times(1)).generate(anyMap());
    }
    @Test void insufficientOrStaleDataDoesNotTriggerGeneration() throws Exception {
        for (var data : List.of(source(false, OffsetDateTime.now()), source(true, OffsetDateTime.now().minusDays(2)))) {
            when(insights.read(store.getId())).thenReturn(data);
            mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.canGenerate").value(false));
        }
        verify(client, never()).generate(anyMap());
        assertTrue(reports.findById(store.getId()).isEmpty());
    }
    @Test void failedRefreshKeepsPreviousReportAndEvidence() throws Exception {
        mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString())))).andExpect(status().isOk());
        var saved = reports.findById(store.getId()).orElseThrow();
        String oldReport = saved.reportJson;
        String oldEvidence = saved.evidenceJson;
        OffsetDateTime generatedAt = saved.generatedAt;
        saved.attemptedAt = OffsetDateTime.now().minusHours(2);
        reports.saveAndFlush(saved);
        when(client.generate(anyMap())).thenThrow(new IllegalStateException("Private failure"));
        mvc.perform(post(path()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.summary").value("Coffee is a leading seller."))
                .andExpect(jsonPath("$.error").isNotEmpty()).andExpect(jsonPath("$.canGenerate").value(false));
        var after = reports.findById(store.getId()).orElseThrow();
        assertEquals(oldReport, after.reportJson); assertEquals(oldEvidence, after.evidenceJson);
        assertEquals(generatedAt, after.generatedAt);
        verify(client, times(2)).generate(anyMap());
    }

}
