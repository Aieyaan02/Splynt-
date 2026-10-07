package com.aieyaan.splynt.clover;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

class CloverInventoryClientTest {
    HttpServer server;
    CloverTokenService tokens;
    CloverInventoryClient client;
    List<String> requests = new ArrayList<>();
    boolean failSecondPage;
    boolean rejectFirstToken;
    AtomicInteger count = new AtomicInteger();

    @BeforeEach void setup() throws Exception {
        tokens = mock(CloverTokenService.class);
        when(tokens.getMerchantId(7L)).thenReturn("merchant-seven");
        when(tokens.getAccessToken(7L)).thenReturn("initial-token");
        when(tokens.refreshAccessToken(7L, "initial-token")).thenReturn("refreshed-token");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/merchants/merchant-seven/items", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            int status = 200;
            String body;
            if (rejectFirstToken && count.getAndIncrement() == 0) { status = 401; body = "{}"; }
            else if (exchange.getRequestURI().getQuery().contains("offset=100")) {
                if (failSecondPage) { status = 503; body = "{}"; }
                else body = "{\"elements\":[{\"id\":\"last\"}]}";
            } else {
                List<String> elements = new ArrayList<>();
                for (int i = 0; i < 100; i++) elements.add("{\"id\":\"item-" + i + "\"}");
                body = "{\"elements\":[" + String.join(",", elements) + "]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/v3/merchants/merchant-seven/categories", exchange -> {
            byte[] bytes = "{\"elements\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new CloverInventoryClient(tokens, "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void cleanup() { server.stop(0); }
    @Test void retrievesBeyondFirstPageUsingStoreMerchant() {
        assertEquals(101, client.getItems(7L).path("elements").size());
        assertEquals(2, requests.size());
        assertTrue(requests.get(1).contains("offset=100"));
        verify(tokens, atLeastOnce()).getMerchantId(7L);
    }
    @Test void failedLaterPageNeverReturnsPartialCatalog() {
        failSecondPage = true;
        assertThrows(IllegalStateException.class, () -> client.getItems(7L));
    }
    @Test void salesCursorIsPreservedAsAFilterAlongsidePagination() {
        server.createContext("/v3/merchants/merchant-seven/orders", exchange -> {
            requests.add(exchange.getRequestURI().getQuery());
            byte[] bytes = "{\"elements\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        assertEquals(0, client.getOrdersModifiedBetween(7L, 123456789L, 123456999L).path("elements").size());
        assertEquals("filter=modifiedTime>=123456789&filter=modifiedTime<123456999&limit=100&offset=0", requests.getFirst());
    }
    @Test void refreshesOnlyRequestedStoresTokenOnUnauthorized() {
        rejectFirstToken = true;
        assertEquals(101, client.getItems(7L).path("elements").size());
        verify(tokens).refreshAccessToken(7L, "initial-token");
    }
    @Test void categoryMembershipsArePaginated() {
        categoryFixture(false);
        var items = client.getItems(7L).path("elements");
        assertEquals("Drinks", items.get(0).path("categories").path("elements").get(0).path("name").asText());
        assertEquals("Drinks", items.get(100).path("categories").path("elements").get(0).path("name").asText());
        assertTrue(requests.stream().anyMatch(uri -> uri.contains("categories/drinks/items?limit=100&offset=100")));
    }
    @Test void failedAssociationPageNeverPublishesPartialCategories() {
        categoryFixture(true);
        assertThrows(IllegalStateException.class, () -> client.getItems(7L));
    }
    @Test void noCategoriesProducesExplicitEmptyMemberships() {
        var item = client.getItems(7L).path("elements").get(0);
        assertTrue(item.path("categories").path("elements").isArray());
        assertEquals(0, item.path("categories").path("elements").size());
    }
    @Test void categoryListPaginationPreservesEveryMembership() {
        server.removeContext("/v3/merchants/merchant-seven/categories");
        server.createContext("/v3/merchants/merchant-seven/categories", exchange -> {
            String body;
            if (exchange.getRequestURI().getPath().endsWith("/items")) {
                body = "{\"elements\":[{\"id\":\"item-0\"}]}";
            } else if (exchange.getRequestURI().getQuery().contains("offset=100")) {
                body = "{\"elements\":[{\"id\":\"last-category\",\"name\":\"Last\"}]}";
            } else {
                List<String> entries = new ArrayList<>();
                for (int i = 0; i < 100; i++) entries.add("{\"id\":\"category-" + i + "\",\"name\":\"Category " + i + "\"}");
                body = "{\"elements\":[" + String.join(",", entries) + "]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        var items = client.getItems(7L).path("elements");
        assertEquals(101, items.get(0).path("categories").path("elements").size());
        assertEquals(0, items.get(1).path("categories").path("elements").size());
    }
    void categoryFixture(boolean fail) {
        server.removeContext("/v3/merchants/merchant-seven/categories");
        server.createContext("/v3/merchants/merchant-seven/categories", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            int status = 200;
            String body;
            if (!exchange.getRequestURI().getPath().endsWith("/items")) {
                body = "{\"elements\":[{\"id\":\"drinks\",\"name\":\"Drinks\"}]}";
            } else if (exchange.getRequestURI().getQuery().contains("offset=100")) {
                status = fail ? 503 : 200;
                body = "{\"elements\":[{\"id\":\"last\"}]}";
            } else {
                List<String> entries = new ArrayList<>();
                for (int i = 0; i < 100; i++) entries.add("{\"id\":\"item-" + i + "\"}");
                body = "{\"elements\":[" + String.join(",", entries) + "]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
    }

    @Test void merchantPropertiesUseTheSelectedStoreMerchant() {
        server.createContext("/v3/merchants/merchant-seven/properties", exchange -> {
            assertEquals("Bearer initial-token", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = "{\"defaultCurrency\":\"CAD\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        assertEquals("CAD", client.getMerchantProperties(7L).path("defaultCurrency").asText());
        verify(tokens).getMerchantId(7L);
    }

    @Test void longSalesWindowsAreBoundedAndOrdersMovingBetweenWindowsAreDeduplicated() {
        long start = 1_700_000_000_000L;
        long day = java.time.Duration.ofDays(1).toMillis();
        server.createContext("/v3/merchants/merchant-seven/orders", exchange -> {
            String query = exchange.getRequestURI().getQuery(); requests.add(query);
            String body = "{\"elements\":[{\"id\":\"moving-order\",\"revision\":" + requests.size() + "}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        var result = client.getOrdersModifiedBetween(7L, start, start + 95 * day).path("elements");
        assertEquals(4, requests.size()); assertEquals(1, result.size());
        assertEquals(4, result.get(0).path("revision").asInt());
        for (int i = 0; i < 4; i++) {
            long lower = start + i * 30 * day;
            long upper = Math.min(start + 95 * day, lower + 30 * day);
            assertEquals("filter=modifiedTime>=" + lower + "&filter=modifiedTime<" + upper + "&limit=100&offset=0", requests.get(i));
        }
    }
    @Test void laterSalesWindowFailureNeverReturnsPartialHistory() {
        server.createContext("/v3/merchants/merchant-seven/orders", exchange -> {
            requests.add(exchange.getRequestURI().getQuery());
            byte[] bytes = "{\"elements\":[{\"id\":\"order-one\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(requests.size() == 2 ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        assertThrows(IllegalStateException.class, () -> client.getOrdersModifiedBetween(7L, 1_700_000_000_000L,
                1_700_000_000_000L + java.time.Duration.ofDays(95).toMillis()));
    }

}
