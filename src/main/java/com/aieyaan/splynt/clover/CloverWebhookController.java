package com.aieyaan.splynt.clover;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.aieyaan.splynt.tenant.StoreRepository;
import tools.jackson.databind.JsonNode;

@RestController
public class CloverWebhookController {
    private final String authCode, appId;
    @Value("${splynt.integrations.clover.webhook-verification-enabled:false}")
    private boolean verificationEnabled;
    private final java.util.concurrent.atomic.AtomicBoolean verificationReceived = new java.util.concurrent.atomic.AtomicBoolean();
    private final CloverOAuthCredentialRepository credentials;
    private final StoreRepository stores;
    private final CloverOAuthStateService states;
    private final TransactionTemplate transactions;

    public CloverWebhookController(@Value("${splynt.integrations.clover.webhook-auth-code:}") String authCode,
            @Value("${splynt.integrations.clover.client-id:}") String appId,
            CloverOAuthCredentialRepository credentials, StoreRepository stores,
            CloverOAuthStateService states, PlatformTransactionManager manager) {
        this.authCode = authCode; this.appId = appId; this.credentials = credentials;
        this.stores = stores; this.states = states; this.transactions = new TransactionTemplate(manager);
    }

    @PostMapping("/api/integrations/clover/webhook")
    public void receive(@RequestHeader(value="X-Clover-Auth", required=false) String supplied,
            @RequestBody JsonNode body) {
        // Temporary operator-enabled setup only. Never accepts lifecycle events without authentication.
        if (verificationEnabled && authCode.isBlank() && body.size() == 1
                && body.path("verificationCode").asText("").matches("[A-Za-z0-9-]{16,80}")) {
            if (verificationReceived.compareAndSet(false, true))
                org.slf4j.LoggerFactory.getLogger(getClass()).info("Clover webhook setup verification code: {}", body.path("verificationCode").asText());
            return;
        }
        if (authCode.isBlank() || appId.isBlank() || supplied == null || !MessageDigest.isEqual(
                authCode.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!appId.equals(body.path("appId").asText()) || !body.path("merchants").isObject())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        // Process only uninstall events. Inventory events never trigger imports here.
        for (var merchant : body.path("merchants").properties()) {
            if (!merchant.getValue().isArray()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            for (JsonNode event : merchant.getValue()) {
                if (!("A:" + appId).equals(event.path("objectId").asText())
                        || !"DELETE".equals(event.path("type").asText())) continue;
                long timestamp = event.path("ts").asLong(0);
                if (timestamp <= 0 || timestamp > System.currentTimeMillis() + 300_000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                Long storeId = credentials.findByMerchantId(merchant.getKey()).map(CloverOAuthCredential::getStoreId).orElse(null);
                if (storeId == null) continue;
                transactions.executeWithoutResult(tx -> {
                    // Same lock order as OAuth/disconnect/import; no in-flight import can commit after this disconnect.
                    if (stores.findLockedById(storeId).isEmpty()) return;
                    credentials.findLockedByStoreId(storeId).ifPresent(connection -> {
                        if (!merchant.getKey().equals(connection.getMerchantId()) || !connection.isConnected()) return;
                        // A delayed event from an earlier installation must not disable a new authorization.
                        if (connection.getAuthorizedAt() != null && Instant.ofEpochMilli(timestamp)
                                .isBefore(connection.getAuthorizedAt().toInstant())) return;
                        states.cancelForStore(storeId);
                        connection.disconnect();
                        connection.markSyncFailed("POS authorization was removed. Reconnect to resume inventory updates.");
                    });
                });
            }
        }
    }
}
