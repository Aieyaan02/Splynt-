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
    private final String merchantId;

    public CloverInventoryClient(
            CloverTokenService tokenService,
            @Value(
                    "${splynt.integrations.clover.base-url:"
                            + "https://apisandbox.dev.clover.com}"
            )
            String baseUrl,
            @Value(
                    "${splynt.integrations.clover.merchant-id:}"
            )
            String merchantId) {

        this.tokenService = tokenService;

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Splynt/0.1")
                .build();

        this.merchantId = merchantId;
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
        String accessToken = tokenService.getAccessToken();

        try {
            return executeGetWithToken(uri, accessToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) {
                throw translateException(exception);
            }

            String refreshedAccessToken =
                    tokenService.refreshAccessToken(accessToken);

            try {
                return executeGetWithToken(
                        uri,
                        refreshedAccessToken
                );
            } catch (RestClientResponseException retryException) {
                throw translateException(retryException);
            }
        }
    }

    private JsonNode executeGetWithToken(
            String uri,
            String accessToken) {

        return restClient.get()
                .uri(uri, merchantId)
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

    private void validateConfiguration() {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalStateException(
                    "Clover merchant ID is not configured"
            );
        }
    }
}
