package com.aieyaan.splynt.clover;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest
class CloverConnectionIntegrationTest {
    static HttpServer provider;
    static AtomicInteger exchanges = new AtomicInteger();
    static AtomicInteger refreshes = new AtomicInteger();
    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/oauth/v2/token", exchange -> {
                exchanges.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String merchant = body.contains("codeB") ? "merchantB" : "merchantA";
                byte[] bytes = ("{\"access_token\":\"access-" + merchant + "\",\"refresh_token\":\"refresh-" + merchant + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            provider.createContext("/oauth/v2/refresh", exchange -> {
                refreshes.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean valid = body.contains("refresh-merchantA");
                byte[] bytes = (valid ? "{\"access_token\":\"rotated-A\",\"refresh_token\":\"rotated-refresh-A\"}" : "{}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(valid ? 200 : 401, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            provider.createContext("/v3/merchants/", exchange -> {
                String merchant = exchange.getRequestURI().getPath().substring("/v3/merchants/".length());
                boolean allowed = ("Bearer access-" + merchant).equals(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = (allowed ? "{\"id\":\"" + merchant + "\"}" : "{}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(allowed ? 200 : 403, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            provider.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry) {
        registry.add("splynt.integrations.clover.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("splynt.integrations.clover.client-id", () -> "test-client");
        registry.add("splynt.integrations.clover.client-secret", () -> "test-secret");
        registry.add("splynt.integrations.clover.redirect-uri", () -> "http://localhost/callback");
        registry.add("splynt.integrations.clover.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @AfterAll static void stopProvider() { provider.stop(0); }
    @Autowired CloverConnectionService connections;
    @Autowired CloverOAuthStateService states;
    @Autowired CloverOAuthAttemptRepository attempts;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired CloverTokenService tokens;
    @Autowired CloverTokenCipher cipher;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    Store store;
    AppUser owner;
    Organization organization;

    @BeforeEach void setup() {
        String id = UUID.randomUUID().toString();
        organization = organizations.saveAndFlush(new Organization("Shop", id));
        store = stores.saveAndFlush(new Store(organization, "Shop", id));
        owner = users.saveAndFlush(new AppUser(id + "@example.com", "hash", "Test", "Owner"));
        memberships.saveAndFlush(new OrganizationMembership(organization, owner, MembershipRole.OWNER));
        exchanges.set(0); refreshes.set(0);
    }
    @AfterEach void cleanup() {
        attempts.deleteAll(); credentials.deleteAll(); memberships.deleteAll();
        stores.deleteAll(); organizations.deleteAll(); users.deleteAll();
    }
    String begin(String binding) {
        return UriComponentsBuilder.fromUriString(connections.begin(store.getId(), owner.getId(), binding))
                .build().getQueryParams().getFirst("state");
    }
    @Test void connectsOnlyVerifiedMerchantAndEncryptsTokens() {
        String state = begin("browser");
        assertEquals(store.getId(), connections.complete(state, "browser", "codeA", "merchantA"));
        var credential = credentials.findByStoreId(store.getId()).orElseThrow();
        assertNotEquals("access-merchantA", credential.getAccessToken());
        assertEquals("access-merchantA", tokens.getAccessToken(store.getId()));
        assertEquals("merchantA", connections.status(store.getId()).merchantId());
        assertThrows(IllegalArgumentException.class, () -> connections.complete(state, "browser", "codeA", "merchantA"));
        assertEquals(1, exchanges.get());
    }
    @Test void rejectsWrongBrowserBeforeExchangingCode() {
        String state = begin("browser");
        assertThrows(IllegalArgumentException.class, () -> connections.complete(state, "other", "codeA", "merchantA"));
        assertEquals(0, exchanges.get());
        assertFalse(connections.status(store.getId()).connected());
    }
    @Test void rejectsExpiredState() {
        attempts.saveAndFlush(new CloverOAuthAttempt(CloverOAuthStateService.hash("expired"),
                CloverOAuthStateService.hash("browser"), store.getId(), owner.getId(), OffsetDateTime.now().minusMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> connections.complete("expired", "browser", "codeA", "merchantA"));
        assertEquals(0, exchanges.get());
    }
    @Test void rechecksAccessAtCallback() {
        String state = begin("browser");
        memberships.deleteAll();
        assertThrows(AccessDeniedException.class, () -> connections.complete(state, "browser", "codeA", "merchantA"));
        assertEquals(0, exchanges.get());
    }
    @Test void employeeCannotInitiateConnection() {
        memberships.deleteAll();
        memberships.saveAndFlush(new OrganizationMembership(organization, owner, MembershipRole.EMPLOYEE));
        assertThrows(AccessDeniedException.class, () -> begin("browser"));
    }
    @Test void doesNotTrustCallbackMerchantId() {
        String state = begin("browser");
        assertThrows(RuntimeException.class, () -> connections.complete(state, "browser", "codeA", "merchantB"));
        assertFalse(connections.status(store.getId()).connected());
        assertThrows(IllegalArgumentException.class, () -> states.consume(state, "browser"));
    }
    @Test void maintainsSeparateTokensForTwoStores() {
        connections.complete(begin("browser"), "browser", "codeA", "merchantA");
        Store second = stores.saveAndFlush(new Store(organization, "Second", "second"));
        String state = states.issue(second.getId(), owner.getId(), "browser");
        connections.complete(state, "browser", "codeB", "merchantB");
        assertEquals("access-merchantA", tokens.getAccessToken(store.getId()));
        assertEquals("access-merchantB", tokens.getAccessToken(second.getId()));
        assertEquals("merchantB", tokens.getMerchantId(second.getId()));
        assertThrows(IllegalStateException.class, () -> tokens.getAccessToken(999999L));
    }
    @Test void rejectsMerchantAlreadyBoundElsewhere() {
        connections.complete(begin("browser"), "browser", "codeA", "merchantA");
        Store second = stores.saveAndFlush(new Store(organization, "Second", "second"));
        String state = states.issue(second.getId(), owner.getId(), "browser");
        assertThrows(IllegalArgumentException.class, () -> connections.complete(state, "browser", "codeA", "merchantA"));
        assertFalse(connections.status(second.getId()).connected());
    }
    @Test void stateCanOnlyBeConsumedOnceUnderConcurrentRequests() throws Exception {
        String state = begin("browser");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> consume = () -> {
                try { states.consume(state, "browser"); return 1; }
                catch (IllegalArgumentException replay) { return 0; }
            };
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            assertEquals(1, first.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    + second.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @Test void concurrentRefreshRotatesOnceAndPersistsNewCredentials() throws Exception {
        connections.complete(begin("browser"), "browser", "codeA", "merchantA");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tokens.refreshAccessToken(store.getId(), "access-merchantA"));
            var second = executor.submit(() -> tokens.refreshAccessToken(store.getId(), "access-merchantA"));
            assertEquals("rotated-A", first.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("rotated-A", second.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, refreshes.get());
        assertEquals("rotated-A", tokens.getAccessToken(store.getId()));
        assertEquals("rotated-refresh-A", cipher.decrypt(credentials.findByStoreId(store.getId()).orElseThrow().getRefreshToken()));
    }
    @Test void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("token");
        String altered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(altered));
    }
}
