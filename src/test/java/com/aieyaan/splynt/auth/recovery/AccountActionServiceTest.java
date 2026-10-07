package com.aieyaan.splynt.auth.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static com.aieyaan.splynt.auth.recovery.AccountActionToken.Purpose.*;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest
class AccountActionServiceTest {
    @Autowired AccountActionService actions;
    @Autowired AccountActionTokenRepository tokens;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    AppUser user;
    @BeforeEach void setup() {
        user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", encoder.encode("initial-password"), "Test", "User"));
    }
    AppUser current() { return users.findById(user.getId()).orElseThrow(); }

    @Test void storesOnlyHashAndResetIsSingleUseWithSessionRevocation() {
        String value = actions.issue(user.getId(), RESET_PASSWORD);
        assertEquals(43, value.length());
        assertTrue(tokens.findById(value).isEmpty());
        assertTrue(tokens.findById(AccountActionService.hash(value)).isPresent());
        actions.resetPassword(value, "replacement-password");
        assertTrue(encoder.matches("replacement-password", current().getPasswordHash()));
        assertEquals(1, current().getCredentialVersion());
        assertThrows(IllegalArgumentException.class, () -> actions.resetPassword(value, "another-password"));
    }
    @Test void wrongPurposeDoesNotConsumeLinkAndVerificationIsSingleUse() {
        String value = actions.issue(user.getId(), VERIFY_EMAIL);
        assertThrows(IllegalArgumentException.class, () -> actions.resetPassword(value, "replacement-password"));
        actions.verifyEmail(value);
        assertTrue(current().isEmailVerified());
        assertThrows(IllegalArgumentException.class, () -> actions.verifyEmail(value));
    }
    @Test void replacementAndExpiryRejectOldLinks() {
        String old = actions.issue(user.getId(), RESET_PASSWORD);
        String latest = actions.issue(user.getId(), RESET_PASSWORD);
        assertThrows(IllegalArgumentException.class, () -> actions.resetPassword(old, "replacement-password"));
        jdbc.update("update account_action_tokens set expires_at = ? where token_hash = ?",
                OffsetDateTime.now().minusMinutes(1), AccountActionService.hash(latest));
        assertThrows(IllegalArgumentException.class, () -> actions.resetPassword(latest, "replacement-password"));
        assertEquals(0, current().getCredentialVersion());
    }
    @Test void emailAndPasswordChangesInvalidateOutstandingLinks() {
        String emailLink = actions.issue(user.getId(), VERIFY_EMAIL);
        var changed = current(); changed.changeEmail(UUID.randomUUID() + "@example.com"); users.saveAndFlush(changed);
        assertThrows(IllegalArgumentException.class, () -> actions.verifyEmail(emailLink));
        changed = current(); changed.changeEmail(user.getEmail()); users.saveAndFlush(changed);
        assertThrows(IllegalArgumentException.class, () -> actions.verifyEmail(emailLink));
        String reset = actions.issue(user.getId(), RESET_PASSWORD);
        changed = current(); changed.changePasswordHash(encoder.encode("different-password")); users.saveAndFlush(changed);
        assertThrows(IllegalArgumentException.class, () -> actions.resetPassword(reset, "replacement-password"));
    }
    @Test void invalidPasswordDoesNotConsumeAValidLink() {
        String value = actions.issue(user.getId(), RESET_PASSWORD);
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> actions.resetPassword(value, "short"));
        actions.resetPassword(value, "replacement-password");
        assertEquals(1, current().getCredentialVersion());
    }
    @Test void simultaneousRedemptionHasExactlyOneWinner() throws Exception {
        String value = actions.issue(user.getId(), RESET_PASSWORD);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> redeem = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try { actions.resetPassword(value, "replacement-password"); return true; }
                catch (IllegalArgumentException rejected) { return false; }
            };
            var a = pool.submit(redeem); var b = pool.submit(redeem); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, current().getCredentialVersion());
    }
}
