package com.aieyaan.splynt.clover;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

@Service
public class CloverTokenService {

    private final CloverOAuthCredentialRepository credentialRepository;
    private final RestClient oauthClient;
    private final TransactionTemplate transactionTemplate;

    private final String merchantId;
    private final String clientId;
    private final String configuredAccessToken;
    private final String configuredRefreshToken;

    private final Object refreshMonitor = new Object();

    public CloverTokenService(
            CloverOAuthCredentialRepository credentialRepository,
            PlatformTransactionManager transactionManager,
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
                    "${splynt.integrations.clover.client-id:"
                            + "4731H0NXZ45WP}"
            )
            String clientId,
            @Value(
                    "${splynt.integrations.clover.access-token:}"
            )
            String configuredAccessToken,
            @Value(
                    "${splynt.integrations.clover.refresh-token:}"
            )
            String configuredRefreshToken) {

        this.credentialRepository = credentialRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        this.oauthClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Splynt/0.1")
                .build();

        this.merchantId = merchantId;
        this.clientId = clientId;
        this.configuredAccessToken = configuredAccessToken;
        this.configuredRefreshToken = configuredRefreshToken;
    }

    public String getAccessToken() {
        validateBaseConfiguration();

        return Objects.requireNonNull(
                transactionTemplate.execute(
                        status -> findOrInitializeCredential()
                                .getAccessToken()
                )
        );
    }

    public String refreshAccessToken(
            String rejectedAccessToken) {

        validateBaseConfiguration();

        synchronized (refreshMonitor) {
            return Objects.requireNonNull(
                    transactionTemplate.execute(
                            status -> refreshWithinTransaction(
                                    rejectedAccessToken
                            )
                    )
            );
        }
    }

    private String refreshWithinTransaction(
            String rejectedAccessToken) {

        CloverOAuthCredential credential =
                findOrInitializeCredential();

        /*
         * Another request may already have refreshed the token while
         * this request waited for the synchronization lock.
         */
        if (!credential.getAccessToken().equals(
                rejectedAccessToken
        )) {
            return credential.getAccessToken();
        }

        JsonNode response;

        try {
            response = oauthClient.post()
                    .uri("/oauth/v2/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id",
                            clientId,
                            "refresh_token",
                            credential.getRefreshToken()
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Clover token refresh returned HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        }

        if (response == null) {
            throw new IllegalStateException(
                    "Clover token refresh returned an empty response"
            );
        }

        String newAccessToken = readRequiredText(
                response,
                "access_token"
        );
        String newRefreshToken = readRequiredText(
                response,
                "refresh_token"
        );

        credential.rotateTokens(
                newAccessToken,
                newRefreshToken,
                readExpiration(
                        response,
                        "access_token_expiration"
                ),
                readExpiration(
                        response,
                        "refresh_token_expiration"
                )
        );

        credentialRepository.saveAndFlush(credential);

        return newAccessToken;
    }

    private CloverOAuthCredential findOrInitializeCredential() {
        return credentialRepository
                .findByMerchantId(merchantId)
                .orElseGet(this::createConfiguredCredential);
    }

    private CloverOAuthCredential createConfiguredCredential() {
        if (configuredAccessToken == null
                || configuredAccessToken.isBlank()) {

            throw new IllegalStateException(
                    "Clover access token is not configured"
            );
        }

        if (configuredRefreshToken == null
                || configuredRefreshToken.isBlank()) {

            throw new IllegalStateException(
                    "Clover refresh token is not configured"
            );
        }

        CloverOAuthCredential credential =
                new CloverOAuthCredential(
                        merchantId,
                        configuredAccessToken,
                        configuredRefreshToken,
                        null,
                        null
                );

        return credentialRepository.saveAndFlush(credential);
    }

    private void validateBaseConfiguration() {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalStateException(
                    "Clover merchant ID is not configured"
            );
        }

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Clover client ID is not configured"
            );
        }
    }

    private String readRequiredText(
            JsonNode response,
            String fieldName) {

        JsonNode value = response.get(fieldName);

        if (value == null
                || value.isNull()
                || value.asText().isBlank()) {

            throw new IllegalStateException(
                    "Clover token refresh did not return "
                            + fieldName
            );
        }

        return value.asText();
    }

    private OffsetDateTime readExpiration(
            JsonNode response,
            String fieldName) {

        JsonNode value = response.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        long epochSeconds = value.asLong();

        if (epochSeconds <= 0) {
            return null;
        }

        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneOffset.UTC
        );
    }
}
