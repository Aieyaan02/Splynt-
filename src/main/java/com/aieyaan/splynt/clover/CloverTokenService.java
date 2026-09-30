package com.aieyaan.splynt.clover;

import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class CloverTokenService {
    private final CloverOAuthCredentialRepository credentials;
    private final CloverTokenCipher cipher;
    private final RestClient client;
    private final TransactionTemplate transactions;
    private final String clientId;

    public CloverTokenService(CloverOAuthCredentialRepository credentials, CloverTokenCipher cipher,
            PlatformTransactionManager transactionManager,
            @Value("${splynt.integrations.clover.base-url:https://apisandbox.dev.clover.com}") String baseUrl,
            @Value("${splynt.integrations.clover.client-id:}") String clientId) {
        this.credentials = credentials; this.cipher = cipher; this.clientId = clientId;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.client = httpClient(baseUrl);
    }

    static RestClient httpClient(String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("Accept", "application/json").defaultHeader("User-Agent", "Splynt/0.2").build();
    }

    public String getMerchantId(Long storeId) { return credential(storeId).getMerchantId(); }
    public String getAccessToken(Long storeId) {
        CloverOAuthCredential credential = credential(storeId);
        String token = cipher.decrypt(credential.getAccessToken());
        if (credential.getAccessTokenExpiresAt() != null
                && credential.getAccessTokenExpiresAt().isBefore(OffsetDateTime.now().plusSeconds(60))) {
            return refreshAccessToken(storeId, token);
        }
        return token;
    }
    private CloverOAuthCredential credential(Long storeId) {
        return credentials.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalStateException("Connect this store to Clover first"));
    }
    public String refreshAccessToken(Long storeId, String rejectedToken) {
        // Database lock coordinates rotating single-use refresh tokens across application instances.
        return transactions.execute(status -> {
            var credential = credentials.findLockedByStoreId(storeId)
                    .orElseThrow(() -> new IllegalStateException("Connect this store to Clover first"));
            String current = cipher.decrypt(credential.getAccessToken());
            if (!current.equals(rejectedToken)) return current;
            JsonNode response = client.post().uri("/oauth/v2/refresh").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("client_id", clientId, "refresh_token", cipher.decrypt(credential.getRefreshToken())))
                    .retrieve().body(JsonNode.class);
            String next = requiredText(response, "access_token");
            credential.rotateTokens(cipher.encrypt(next), cipher.encrypt(requiredText(response, "refresh_token")),
                    expiration(response, "access_token_expiration"), expiration(response, "refresh_token_expiration"));
            credentials.saveAndFlush(credential);
            return next;
        });
    }
    static String requiredText(JsonNode response, String field) {
        if (response == null || !response.path(field).isTextual() || response.path(field).asText().isBlank())
            throw new IllegalStateException("Clover returned an incomplete authorization response");
        return response.path(field).asText();
    }
    static OffsetDateTime expiration(JsonNode response, String field) {
        long epoch = response.path(field).asLong(0);
        return epoch <= 0 ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
    }
}
