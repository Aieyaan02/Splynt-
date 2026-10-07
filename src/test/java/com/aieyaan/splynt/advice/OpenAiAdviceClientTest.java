package com.aieyaan.splynt.advice;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenAiAdviceClientTest {
    HttpServer server;
    OpenAiAdviceClient client;
    JsonMapper json = new JsonMapper();
    JsonNode request;
    String authorization;
    String response;
    int status = 200;
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            request = json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new OpenAiAdviceClient("server-only-test-key", "configured-model", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        response = envelope(content());
    }
    @AfterEach void stop() { server.stop(0); }
    static AdviceContent content() {
        return new AdviceContent("Coffee is a leading seller.",
                List.of(new AdviceContent.Action("Review coffee stock", "Observed sales support a review.", "Check supplier lead time.", List.of("product-1"))),
                List.of(new AdviceContent.Experiment("Small pastry selection", "Could complement coffee; unproven.", "Offer a small trial.", "Track units and waste.", List.of("product-1"))),
                List.of("Seasonality is not established; no local market data is available."));
    }
    String envelope(AdviceContent content) {
        return json.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message", "content",
                List.of(Map.of("type", "output_text", "text", json.writeValueAsString(content)))))));
    }
    @Test void usesStructuredResponsesAndDoesNotRequestProviderStorage() {
        var result = client.generate(Map.of("product-1", Map.of("name", "Coffee", "units", 30)));
        assertEquals("Small pastry selection", result.productExperiments().getFirst().productIdea());
        assertEquals("Bearer server-only-test-key", authorization);
        assertEquals("configured-model", request.path("model").asText());
        assertFalse(request.path("store").asBoolean());
        assertEquals("json_schema", request.path("text").path("format").path("type").asText());
        assertTrue(request.path("text").path("format").path("strict").asBoolean());
        assertFalse(request.path("input").asText().contains("server-only-test-key"));
        assertFalse(request.has("tools"));
    }
    @Test void unknownEvidenceIsRejectedInsteadOfShowingInventedSupport() {
        assertThrows(IllegalStateException.class, () -> client.generate(Map.of("product-2", "Other store")));
    }
    @Test void refusalIsNotParsedAsAdvice() {
        response = "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}";
        assertThrows(IllegalStateException.class, () -> client.generate(Map.of("product-1", "Coffee")));
    }
    @Test void incompleteOutputIsNotDisplayedAsACompleteReport() {
        response = response.replace("\"completed\"", "\"incomplete\"");
        assertThrows(IllegalStateException.class, () -> client.generate(Map.of("product-1", "Coffee")));
    }
    @Test void missingConfigurationNeverMakesARequest() {
        var disabled = new OpenAiAdviceClient("", "", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        assertFalse(disabled.configured());
        assertThrows(IllegalStateException.class, () -> disabled.generate(Map.of()));
        assertNull(request);
    }
    @Test void malformedContentCannotReachTheUi() {
        response = envelope(new AdviceContent("", List.of(), List.of(), List.of()));
        assertThrows(IllegalStateException.class, () -> client.generate(Map.of()));
    }
}
