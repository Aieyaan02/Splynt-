package com.aieyaan.splynt.clover;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CloverOAuthStateService {
    private final CloverOAuthAttemptRepository attempts;
    public CloverOAuthStateService(CloverOAuthAttemptRepository attempts) { this.attempts = attempts; }
    public static String randomValue() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String hash(String value) {
        if (value == null || value.length() > 256) throw new IllegalArgumentException("Invalid connection session");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @Transactional
    public String issue(Long storeId, Long userId, String browserBinding) {
        attempts.deleteExpired(OffsetDateTime.now());
        String state = randomValue();
        attempts.save(new CloverOAuthAttempt(hash(state), hash(browserBinding), storeId, userId, OffsetDateTime.now().plusMinutes(10)));
        return state;
    }
    // Commit consumption before contacting Clover, even if token exchange later fails.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CloverOAuthAttempt consume(String state, String browserBinding) {
        CloverOAuthAttempt attempt = attempts.findForConsumption(hash(state))
                .orElseThrow(() -> new IllegalArgumentException("Connection session expired or was already used. Start again."));
        if (!attempt.getExpiresAt().isAfter(OffsetDateTime.now())
                || !MessageDigest.isEqual(attempt.getBrowserHash().getBytes(StandardCharsets.US_ASCII),
                        hash(browserBinding).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Connection session expired or belongs to another browser. Start again.");
        }
        attempts.delete(attempt);
        attempts.flush();
        return attempt;
    }
}
