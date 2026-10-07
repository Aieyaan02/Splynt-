package com.aieyaan.splynt.auth.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static com.aieyaan.splynt.auth.recovery.AccountActionToken.Purpose.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.security.JwtTokenService;

@SpringBootTest
class AccountRecoveryHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired AccountEmailRequestGuardRepository guard;
    @Autowired AccountEmailRequestRepository requests;
    @Autowired AccountEmailJobRepository jobs;
    @Autowired AccountEmailQueue queue;
    @Autowired AccountActionService actions;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService tokens;
    @MockitoBean AccountEmailDelivery delivery;
    MockMvc mvc;
    AppUser user;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (!guard.existsById(1)) guard.saveAndFlush(new AccountEmailRequestGuard(1));
        requests.deleteAll();
        user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", encoder.encode("initial-password"), "Test", "User"));
        when(delivery.configured()).thenReturn(true);
    }
    String request(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/request").contentType("application/json")
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }
    @Test void unknownDisabledAndLimitedAddressesReceiveSameResponse() throws Exception {
        String expected = request(user.getEmail());
        assertEquals(expected, request(UUID.randomUUID() + "@example.com"));
        user.disable(); users.saveAndFlush(user);
        assertEquals(expected, request(user.getEmail()));
        assertEquals(expected, request(user.getEmail()));
        assertEquals(expected, request(user.getEmail()));
        assertEquals(4, requests.count()); // Three for this address, one unknown.
        assertFalse(expected.contains("token"));
    }
    @Test void requestedEmailCanResetPasswordAndRevokeOldSession() throws Exception {
        String oldToken = tokens.generateAccessToken(user).value();
        request(user.getEmail());
        var job = jobs.findFirstByUserIdAndPurposeAndStateOrderByIdDesc(user.getId(), RESET_PASSWORD, "PENDING").orElseThrow();
        var link = new AtomicReference<String>();
        doAnswer(call -> { link.set(call.getArgument(2)); return null; }).when(delivery).send(eq(user.getEmail()), eq(RESET_PASSWORD), anyString());
        queue.deliver(job.getId());
        String body = "{\"token\":\"" + link.get() + "\",\"password\":\"replacement-password\"}";
        mvc.perform(post("/api/auth/password-reset/confirm").contentType("application/json").content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"replacement-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
    @Test void verificationRequiresExplicitPostAndInvalidInputIsRejected() throws Exception {
        String token = actions.issue(user.getId(), VERIFY_EMAIL);
        mvc.perform(post("/api/auth/email-verification/confirm").contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().isNoContent());
        assertTrue(users.findById(user.getId()).orElseThrow().isEmailVerified());
        mvc.perform(post("/api/auth/password-reset/request").contentType("application/json").content("{\"email\":\"not-email\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType("application/json").content("{\"token\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void disabledDeliveryIsExplicitAndDoesNotQueue() throws Exception {
        when(delivery.configured()).thenReturn(false);
        mvc.perform(get("/api/auth/recovery/status")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/auth/password-reset/request").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\"}")).andExpect(status().isServiceUnavailable());
        assertEquals(0, requests.count());
    }
    @Test void concurrentRequestsRespectSharedPerAddressLimit() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var futures = new java.util.ArrayList<Future<String>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return request(user.getEmail()); }));
            start.countDown();
            String expected = futures.getFirst().get(15, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(expected, future.get(15, TimeUnit.SECONDS));
        }
        assertEquals(3, requests.count());
    }
    @Test void globalLimitCapsDifferentAddresses() throws Exception {
        for (int i = 0; i < 22; i++) request(UUID.randomUUID() + "@example.com");
        assertEquals(20, requests.count());
    }
}
