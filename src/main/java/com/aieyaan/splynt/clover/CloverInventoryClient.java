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
        return getAllPages(storeId,
                "/v3/merchants/{merchantId}/items"
        );
    }

    public JsonNode getItemStocks(Long storeId) {
        return getAllPages(storeId,
                "/v3/merchants/{merchantId}/item_stocks"
        );
    }

    private JsonNode getAllPages(Long storeId, String path) {
        var result = new tools.jackson.databind.json.JsonMapper().createObjectNode();
        var elements = result.putArray("elements");
        var seen = new java.util.HashSet<String>();
        final int pageSize = 100;
        for (int offset = 0; offset < 1_000_000; offset += pageSize) {
            JsonNode response = executeGet(storeId, path + "?limit=" + pageSize + "&offset=" + offset);
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
