package com.aieyaan.splynt.clover;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real application services/security/database; only the external Clover server is simulated. */
@SpringBootTest
class StoreOnboardingAcceptanceTest {
    static final String MERCHANT = "acceptance-" + UUID.randomUUID();
    static final HttpServer provider;
    static volatile int quantity = 2;
    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body;
                int status = 200;
                if (path.equals("/oauth/v2/token")) {
                    body = "{\"access_token\":\"acceptance-access\",\"refresh_token\":\"acceptance-refresh\"}";
                } else if (!"Bearer acceptance-access".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    status = 401; body = "{}";
                } else if (path.equals("/v3/merchants/" + MERCHANT)) {
                    body = "{\"id\":\"" + MERCHANT + "\"}";
                } else if (path.endsWith("/item_stocks")) {
                    body = "{\"elements\":[{\"item\":{\"id\":\"coffee\"},\"quantity\":" + quantity + "}]}";
                } else if (path.endsWith("/properties")) {
                    body = "{\"defaultCurrency\":\"USD\"}";
                } else if (path.endsWith("/categories") || path.endsWith("/orders")) {
                    body = "{\"elements\":[]}";
                } else if (path.endsWith("/items")) {
                    body = "{\"elements\":[{\"id\":\"coffee\",\"name\":\"Coffee\",\"code\":\"COFFEE\",\"priceType\":\"FIXED\",\"price\":599,\"cost\":250}]}";
                } else { status = 404; body = "{}"; }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            provider.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {
        registry.add("splynt.integrations.clover.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("splynt.integrations.clover.client-id", () -> "acceptance-client");
        registry.add("splynt.integrations.clover.client-secret", () -> "acceptance-secret");
        registry.add("splynt.integrations.clover.redirect-uri", () -> "https://staging.example/api/integrations/clover/connect");
        registry.add("splynt.integrations.clover.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @AfterAll static void closeProvider() { provider.stop(0); }
    @Autowired WebApplicationContext context;
    @Autowired com.aieyaan.splynt.inventory.InventoryMovementRepository movements;
    MockMvc mvc;
    final JsonMapper json = new JsonMapper();
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    record Account(long store, String token) {}
    Account registerAndLogin() throws Exception {
        String slug = "shop-" + UUID.randomUUID();
        String email = slug + "@example.com";
        var request = json.createObjectNode().put("email", email).put("password", "Acceptance-password-27")
                .put("firstName", "Alex").put("lastName", "Retailer").put("organizationName", "Acceptance Shop")
                .put("organizationSlug", slug).put("storeName", "Main Store").put("storeSlug", slug);
        JsonNode registered = json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(request.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var login = json.createObjectNode().put("email", email).put("password", "Acceptance-password-27");
        JsonNode authenticated = json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(login.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Account(registered.path("storeId").asLong(), authenticated.path("accessToken").asText());
    }
    @Test void signupThroughConnectionAndLowStockWithRealBearerAuthentication() throws Exception {
        Account owner = registerAndLogin();
        assertTrue(owner.store() > 0); assertFalse(owner.token().isBlank());
        String auth = "Bearer " + owner.token();
        String integration = "/api/stores/" + owner.store() + "/integrations/clover";
        String products = "/api/stores/" + owner.store() + "/products";
        mvc.perform(get("/api/me").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get(products).header("Authorization", auth)).andExpect(status().isOk()).andExpect(content().json("[]"));
        var begin = mvc.perform(post(integration + "/connect").header("Authorization", auth).secure(true))
                .andExpect(status().isOk()).andReturn().getResponse();
        String authorizationUrl = json.readTree(begin.getContentAsString()).path("authorizationUrl").asText();
        String state = UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams().getFirst("state");
        Cookie binding = begin.getCookie("splynt_clover_session");
        assertNotNull(binding); assertTrue(binding.isHttpOnly()); assertTrue(binding.getSecure());
        mvc.perform(get("/api/integrations/clover/connect").param("state", state).param("code", "test-code")
                .param("merchant_id", MERCHANT).cookie(binding).secure(true))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/?clover=connected&store=" + owner.store() + "#/app"));
        mvc.perform(post(integration + "/sync").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.issueCount").value(0));
        mvc.perform(get(products + "/low-stock").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Coffee"))
                .andExpect(jsonPath("$[0].quantity").value(2)).andExpect(jsonPath("$[0].suggestedReorderQuantity").value(18))
                .andExpect(jsonPath("$[0].cloverDetails.money.price").value("5.99"));
        mvc.perform(get(integration).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true)).andExpect(jsonPath("$.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        Account outsider = registerAndLogin();
        mvc.perform(get(products).header("Authorization", "Bearer " + outsider.token())).andExpect(status().isForbidden());
        mvc.perform(post(integration + "/sync").header("Authorization", "Bearer " + outsider.token())).andExpect(status().isForbidden());
        quantity = 12;
        mvc.perform(post(integration + "/sync").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get(products + "/low-stock").header("Authorization", auth)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(products).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].quantity").value(12));
        // Repeating the same snapshot must not duplicate history or invent sales.
        mvc.perform(post(integration + "/sync").header("Authorization", auth)).andExpect(status().isOk());
        var catalog = json.readTree(mvc.perform(get(products).header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        var history = movements.findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(owner.store(), catalog.get(0).path("id").asLong());
        assertEquals(2, history.size());
        assertTrue(history.stream().allMatch(m -> m.getMovementType() == com.aieyaan.splynt.inventory.InventoryMovementType.ADJUSTMENT));
        assertTrue(history.stream().allMatch(m -> m.getExternalReference() == null));
        mvc.perform(get(products)).andExpect(status().isUnauthorized());
    }
}
