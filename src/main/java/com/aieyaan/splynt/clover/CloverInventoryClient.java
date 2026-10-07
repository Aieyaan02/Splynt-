package com.aieyaan.splynt.clover;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

@Component
public class CloverInventoryClient {

    private final RestClient restClient;
    private final CloverTokenService tokenService;


    public CloverInventoryClient(
            CloverTokenService tokenService,
            @Value(
                    "${splynt.integrations.clover.base-url:"
                            + "https://apisandbox.dev.clover.com}"
            )
            String baseUrl) {

        this.tokenService = tokenService;

        this.restClient = CloverTokenService.httpClient(baseUrl);
    }

    public JsonNode getItems(Long storeId) {
        JsonNode catalog = getAllPages(storeId, "/v3/merchants/{merchantId}/items");
        var categoriesByItem = new java.util.HashMap<String, tools.jackson.databind.node.ArrayNode>();
        var mapper = new tools.jackson.databind.json.JsonMapper();
        // Page associations explicitly: nested expansions are not a completeness guarantee.
        // Traverse by category rather than issuing an extra request for every inventory item.
        for (JsonNode category : getAllPages(storeId, "/v3/merchants/{merchantId}/categories").path("elements")) {
            String categoryId = category.path("id").asText("");
            if (!categoryId.matches("[A-Za-z0-9_-]{1,64}"))
                throw new IllegalStateException("Invalid Clover category identifier");
            for (JsonNode item : getAllPages(storeId,
                    "/v3/merchants/{merchantId}/categories/" + categoryId + "/items").path("elements")) {
                String itemId = item.path("id").asText("");
                if (itemId.isBlank()) throw new IllegalStateException("Invalid Clover category association");
                categoriesByItem.computeIfAbsent(itemId, ignored -> mapper.createArrayNode()).add(category);
            }
        }
        // Publish the snapshot only once every category and association page succeeded.
        for (JsonNode item : catalog.path("elements")) {
            if (item instanceof tools.jackson.databind.node.ObjectNode object) {
                var categories = mapper.createObjectNode();
                categories.set("elements", categoriesByItem.getOrDefault(item.path("id").asText(""), mapper.createArrayNode()));
                object.set("categories", categories);
            }
        }
        return catalog;
    }

    public JsonNode getMerchantProperties(Long storeId) {
        return executeGet(storeId, "/v3/merchants/{merchantId}/properties");
    }

    public JsonNode getItemStocks(Long storeId) {
        return getAllPages(storeId,
                "/v3/merchants/{merchantId}/item_stocks"
        );
    }

    public JsonNode getOrdersModifiedSince(Long storeId, long milliseconds) {
        return getOrdersModifiedBetween(storeId, milliseconds, System.currentTimeMillis());
    }

    public JsonNode getOrdersModifiedBetween(Long storeId, long fromInclusive, long untilExclusive) {
        if (fromInclusive < 0 || untilExclusive < fromInclusive)
            throw new IllegalArgumentException("Invalid sales import time range");
        final long window = java.time.Duration.ofDays(30).toMillis();
        var byId = new java.util.LinkedHashMap<String, JsonNode>();
        for (long start = fromInclusive; start < untilExclusive;) {
            long end = start + Math.min(window, untilExclusive - start);
            JsonNode page = getAllPages(storeId, "/v3/merchants/{merchantId}/orders?filter=modifiedTime>="
                    + start + "&filter=modifiedTime<" + end);
            for (JsonNode order : page.path("elements")) {
                String id = order.path("id").asText("");
                if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalStateException("Invalid Clover order identifier");
                // A changed order may move into a later window during the read; retain its latest snapshot.
                byId.put(id, order);
                if (byId.size() > 1_000_000) throw new IllegalStateException("Clover sales exceeded the supported import size");
            }
            start = end;
        }
        var result = new tools.jackson.databind.json.JsonMapper().createObjectNode();
        var elements = result.putArray("elements");
        byId.values().forEach(elements::add);
        return result;
    }

    public JsonNode getOrderLines(Long storeId, String orderId) {
        if (!orderId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalStateException("Invalid Clover order identifier");
        return getAllPages(storeId, "/v3/merchants/{merchantId}/orders/" + orderId + "/line_items");
    }

    private JsonNode getAllPages(Long storeId, String path) {
        var result = new tools.jackson.databind.json.JsonMapper().createObjectNode();
        var elements = result.putArray("elements");
        var seen = new java.util.HashSet<String>();
        final int pageSize = 100;
        for (int offset = 0; offset < 1_000_000; offset += pageSize) {
            JsonNode response = executeGet(storeId, path + (path.contains("?") ? "&" : "?") + "limit=" + pageSize + "&offset=" + offset);
            if (response == null || !response.path("elements").isArray())
                throw new IllegalStateException("Clover returned an invalid inventory page");
            JsonNode page = response.path("elements");
            for (JsonNode element : page) {
                String id = element.path("id").asText(element.path("item").path("id").asText(null));
                if (id != null && !seen.add(id))
                    throw new IllegalStateException("Clover inventory changed during pagination. Retry synchronization.");
                elements.add(element);
            }
            if (page.size() < pageSize) return result;
        }
        throw new IllegalStateException("Clover inventory exceeded the supported import size");
    }

    public String getMerchantId(Long storeId) {
        return tokenService.getMerchantId(storeId);
    }

    private JsonNode executeGet(Long storeId, String uri) {
        String accessToken = tokenService.getAccessToken(storeId);

        try {
            return executeGetWithToken(storeId, uri, accessToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) {
                throw translateException(exception);
            }

            String refreshedAccessToken =
                    tokenService.refreshAccessToken(storeId, accessToken);

            try {
                return executeGetWithToken(
                        storeId, uri,
                        refreshedAccessToken
                );
            } catch (RestClientResponseException retryException) {
                throw translateException(retryException);
            }
        }
    }

    private JsonNode executeGetWithToken(
            Long storeId, String uri,
            String accessToken) {

        return restClient.get()
                .uri(uri, getMerchantId(storeId))
                .headers(headers ->
                        headers.setBearerAuth(accessToken)
                )
                .retrieve()
                .body(JsonNode.class);
    }

    private IllegalStateException translateException(
            RestClientResponseException exception) {

        return new IllegalStateException(
                "Clover returned HTTP "
                        + exception.getStatusCode().value()
                        + " while synchronizing inventory",
                exception
        );
    }

}
