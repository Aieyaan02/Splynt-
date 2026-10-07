package com.aieyaan.splynt.clover;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import com.aieyaan.splynt.security.StoreAuthorizationService;
import com.aieyaan.splynt.tenant.StoreRepository;
import tools.jackson.databind.JsonNode;

@Service
public class CloverConnectionService {
    private final CloverOAuthStateService states;
    private final CloverOAuthCredentialRepository credentials;
    private final CloverTokenCipher cipher;
    private final StoreAuthorizationService authorization;
    private final StoreRepository stores;
    private final TransactionTemplate transactions;
    private final RestClient client;
    private final String clientId, clientSecret, redirectUri, authorizationUrl;

    public CloverConnectionService(CloverOAuthStateService states, CloverOAuthCredentialRepository credentials,
            CloverTokenCipher cipher, StoreAuthorizationService authorization, StoreRepository stores,
            PlatformTransactionManager transactionManager,
            @Value("${splynt.integrations.clover.base-url:https://apisandbox.dev.clover.com}") String baseUrl,
            @Value("${splynt.integrations.clover.authorization-url:https://sandbox.dev.clover.com/oauth/v2/authorize}") String authorizationUrl,
            @Value("${splynt.integrations.clover.client-id:}") String clientId,
            @Value("${splynt.integrations.clover.client-secret:}") String clientSecret,
            @Value("${splynt.integrations.clover.redirect-uri:}") String redirectUri) {
        this.states = states; this.credentials = credentials; this.cipher = cipher; this.authorization = authorization;
        this.stores = stores; this.transactions = new TransactionTemplate(transactionManager);
        this.client = CloverTokenService.httpClient(baseUrl); this.clientId = clientId; this.clientSecret = clientSecret;
        this.redirectUri = redirectUri; this.authorizationUrl = authorizationUrl;
    }

    public String begin(Long storeId, Long userId, String browserBinding) {
        if (!authorization.canManageUser(userId, storeId)) throw new AccessDeniedException("Only store owners and admins can connect Clover");
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank())
            throw new IllegalStateException("Clover setup is not configured. Contact the Splynt administrator.");
        cipher.validateConfiguration();
        String state = states.issue(storeId, userId, browserBinding);
        return UriComponentsBuilder.fromUriString(authorizationUrl).queryParam("client_id", clientId)
                .queryParam("response_type", "code").queryParam("redirect_uri", redirectUri)
                .queryParam("state", state).build().encode().toUriString();
    }

    public Long complete(String state, String binding, String code, String merchantId) {
        CloverOAuthAttempt attempt = states.consume(state, binding);
        if (!authorization.canManageUser(attempt.getUserId(), attempt.getStoreId()))
            throw new AccessDeniedException("Store access changed. Start the connection again.");
        if (code == null || code.isBlank() || code.length() > 2048
                || merchantId == null || !merchantId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Clover authorization was incomplete. Start again.");
        JsonNode tokens = client.post().uri("/oauth/v2/token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", clientId, "client_secret", clientSecret, "code", code))
                .retrieve().body(JsonNode.class);
        String access = CloverTokenService.requiredText(tokens, "access_token");
        String refresh = CloverTokenService.requiredText(tokens, "refresh_token");
        // The callback's merchant_id is untrusted until the granted token can read that merchant.
        JsonNode merchant = client.get().uri("/v3/merchants/{id}", merchantId)
                .headers(headers -> headers.setBearerAuth(access)).retrieve().body(JsonNode.class);
        if (!merchantId.equals(CloverTokenService.requiredText(merchant, "id")))
            throw new IllegalArgumentException("Clover merchant identity could not be verified");
        transactions.executeWithoutResult(status -> {
            stores.findLockedById(attempt.getStoreId()).orElseThrow();
            if (!authorization.canManageUser(attempt.getUserId(), attempt.getStoreId()))
                throw new AccessDeniedException("Store access changed. Start the connection again.");
            var storeConnection = credentials.findLockedByStoreId(attempt.getStoreId());
            if (storeConnection.isPresent() && !storeConnection.get().getMerchantId().equals(merchantId))
                throw new IllegalArgumentException("This store is already linked to a different merchant. Create a separate store.");
            var merchantConnection = credentials.findByMerchantId(merchantId);
            if (merchantConnection.isPresent() && merchantConnection.get().getStoreId() != null
                    && !merchantConnection.get().getStoreId().equals(attempt.getStoreId()))
                throw new IllegalArgumentException("This Clover merchant is already connected to another store");
            var credential = merchantConnection.orElseGet(() -> new CloverOAuthCredential(
                    merchantId, cipher.encrypt(access), cipher.encrypt(refresh), null, null));
            credential.assignStore(attempt.getStoreId());
            credential.rotateTokens(cipher.encrypt(access), cipher.encrypt(refresh),
                    CloverTokenService.expiration(tokens, "access_token_expiration"),
                    CloverTokenService.expiration(tokens, "refresh_token_expiration"));
            credentials.saveAndFlush(credential);
        });
        return attempt.getStoreId();
    }

    public ConnectionStatus status(Long storeId) {
        return credentials.findByStoreId(storeId)
                .map(c -> new ConnectionStatus(true, c.getMerchantId(), c.getLastSyncedAt(), c.getLastSyncError(), c.getInventoryIssueCount(), c.getInventoryIssues()))
                .orElse(new ConnectionStatus(false, null, null, null, 0, java.util.List.of()));
    }
    public record ConnectionStatus(boolean connected, String merchantId, OffsetDateTime lastSyncedAt, String lastSyncError, int issueCount, java.util.List<com.aieyaan.splynt.clover.dto.CloverImportIssue> issues) {}
}
