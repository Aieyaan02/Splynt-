package com.aieyaan.splynt.auth.recovery;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.tenant.AppUserRepository;

@Service
public class AccountEmailRequestService {
    private final AccountEmailRequestGuardRepository guard;
    private final AccountEmailRequestRepository requests;
    private final AppUserRepository users;
    private final AccountEmailQueue queue;
    private final AccountEmailDelivery delivery;
    public AccountEmailRequestService(AccountEmailRequestGuardRepository guard, AccountEmailRequestRepository requests,
            AppUserRepository users, AccountEmailQueue queue, AccountEmailDelivery delivery) {
        this.guard = guard; this.requests = requests; this.users = users; this.queue = queue; this.delivery = delivery;
    }
    @Transactional
    public void request(String email, AccountActionToken.Purpose purpose) {
        if (!delivery.configured()) throw new IllegalStateException("Account email is not available yet. Please try again later.");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("Required digest is unavailable", failure); }
        guard.lock().orElseThrow(() -> new IllegalStateException("Account email is temporarily unavailable."));
        var now = OffsetDateTime.now();
        requests.deleteExpired(now.minusHours(1));
        if (requests.countByCreatedAtAfter(now.minusMinutes(1)) >= 20
                || requests.countByEmailHashAndCreatedAtAfter(hash, now.minusHours(1)) >= 3) return;
        requests.saveAndFlush(new AccountEmailRequest(hash, now));
        users.findByEmailIgnoreCase(normalized).filter(u -> u.isEnabled()
                && !(purpose == AccountActionToken.Purpose.VERIFY_EMAIL && u.isEmailVerified()))
                .ifPresent(user -> queue.enqueue(user.getId(), purpose));
    }
}
