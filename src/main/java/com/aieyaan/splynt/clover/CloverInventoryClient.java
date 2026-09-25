package com.aieyaan.splynt.clover;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

@Component
public class CloverInventoryClient {

    private final RestClient restClient;
    private final String merchantId;
    private final String accessToken;

    public CloverInventoryClient(
            @Value(
                    "${splynt.integrations.clover.base-url:"
                            + "https://apisandbox.dev.clover.com}"
            )
            String baseUrl,
            @Value(
                    "${splynt.integrations.clover.merchant-id:}"
            )
            String merchantId,
            @Value(
                    "${splynt.integrations.clover.access-token:}"
            )
            String accessToken) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Splynt/0.1")
                .build();

        this.merchantId = merchantId;
        this.accessToken = accessToken;
    }

    public JsonNode getItems() {
        validateConfiguration();

        return executeGet(
                "/v3/merchants/{merchantId}/items?limit=1000"
        );
    }

    public JsonNode getItemStocks() {
        validateConfiguration();

        return executeGet(
                "/v3/merchants/{merchantId}/item_stocks?limit=1000"
        );
    }

    public String getMerchantId() {
        return merchantId;
    }

    private JsonNode executeGet(String uri) {
        try {
            return restClient.get()
                    .uri(uri, merchantId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Clover returned HTTP "
                            + exception.getStatusCode().value()
                            + " while synchronizing inventory",
                    exception
            );
        }
    }

    private void validateConfiguration() {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalStateException(
                    "Clover merchant ID is not configured"
            );
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Clover access token is not configured"
            );
        }
    }
}