package com.aieyaan.splynt.auth.recovery;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.constraints.*;
import com.aieyaan.splynt.auth.validation.PasswordByteLimit;
import com.aieyaan.splynt.tenant.*;
import static com.aieyaan.splynt.auth.recovery.AccountActionToken.Purpose.*;

@Service
@Validated
public class AccountActionService {
    private final AccountActionTokenRepository tokens;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    public AccountActionService(AccountActionTokenRepository tokens, AppUserRepository users, PasswordEncoder encoder) {
        this.tokens = tokens; this.users = users; this.encoder = encoder;
    }
    // Internal API only: deliver the returned value through a verified email adapter, never a public response or log.
    @Transactional
    public String issue(Long userId, AccountActionToken.Purpose purpose) {
        Objects.requireNonNull(purpose);
        var user = users.findLockedById(userId).filter(AppUser::isEnabled).orElseThrow(AccountActionService::invalid);
        tokens.removePrevious(userId, purpose);
        byte[] entropy = new byte[32]; random.nextBytes(entropy);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        var expiry = purpose == RESET_PASSWORD ? OffsetDateTime.now().plusMinutes(30) : OffsetDateTime.now().plusHours(24);
        tokens.saveAndFlush(new AccountActionToken(hash(value), user, purpose, expiry));
        return value;
    }
    @Transactional
    public void resetPassword(String value, @NotBlank @Size(min = 10, max = 72) @PasswordByteLimit String password) {
        var action = consume(value, RESET_PASSWORD);
        action.user().changePasswordHash(encoder.encode(password));
        tokens.delete(action.token());
        users.flush();
    }
    @Transactional
    public void verifyEmail(String value) {
        var action = consume(value, VERIFY_EMAIL);
        action.user().markEmailVerified();
        tokens.delete(action.token());
        users.flush();
    }
    private Action consume(String value, AccountActionToken.Purpose purpose) {
        String hash = hash(value);
        var initial = tokens.findById(hash).orElseThrow(AccountActionService::invalid);
        // Same lock order as issuance: user first, then token. Re-query after acquiring the user lock.
        var user = users.findLockedById(initial.getUserId()).orElseThrow(AccountActionService::invalid);
        var token = tokens.findLocked(hash).orElseThrow(AccountActionService::invalid);
        if (!token.validFor(user, purpose, OffsetDateTime.now())) throw invalid();
        return new Action(user, token);
    }
    static String hash(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("Required digest is unavailable", failure); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("This link is invalid or expired. Request a new email."); }
    private record Action(AppUser user, AccountActionToken token) { }
}
