package com.aieyaan.splynt.auth.recovery;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest
class AccountRecoveryMaintenanceTest {
    @Autowired AccountRecoveryMaintenance maintenance;
    @Autowired AccountEmailJobRepository jobs;
    @Autowired AccountActionTokenRepository tokens;
    @Autowired AccountEmailRequestRepository requests;
    @Autowired AccountActionService actions;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test void cleanupRetainsUsableTokensAndRecentJobsButRemovesExpiredData() {
        var user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", "hash", "Test", "User"));
        String valid = actions.issue(user.getId(), AccountActionToken.Purpose.VERIFY_EMAIL);
        String expired = actions.issue(user.getId(), AccountActionToken.Purpose.RESET_PASSWORD);
        jdbc.update("update account_action_tokens set expires_at = ? where token_hash = ?", OffsetDateTime.now().minusMinutes(1), AccountActionService.hash(expired));
        var recent = jobs.saveAndFlush(new AccountEmailJob(user, AccountActionToken.Purpose.VERIFY_EMAIL));
        var completed = new AccountEmailJob(user, AccountActionToken.Purpose.VERIFY_EMAIL); completed.complete("SENT");
        completed = jobs.saveAndFlush(completed);
        var stale = jobs.saveAndFlush(new AccountEmailJob(user, AccountActionToken.Purpose.RESET_PASSWORD));
        jdbc.update("update account_email_jobs set created_at = ? where id = ?", OffsetDateTime.now().minusHours(2), stale.getId());
        var old = jobs.saveAndFlush(new AccountEmailJob(user, AccountActionToken.Purpose.RESET_PASSWORD));
        jdbc.update("update account_email_jobs set created_at = ? where id = ?", OffsetDateTime.now().minusDays(8), old.getId());
        String oldHash = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32);
        String recentHash = UUID.randomUUID().toString().replace("-", "") + "b".repeat(32);
        requests.saveAndFlush(new AccountEmailRequest(oldHash, OffsetDateTime.now().minusHours(2)));
        requests.saveAndFlush(new AccountEmailRequest(recentHash, OffsetDateTime.now()));
        var result = maintenance.clean();
        assertTrue(result.removedJobs() >= 1); assertTrue(result.cancelledJobs() >= 1);
        assertTrue(result.removedTokens() >= 1); assertTrue(result.removedRequests() >= 1);
        assertFalse(jobs.existsById(old.getId()));
        assertEquals("CANCELLED", jobs.findById(stale.getId()).orElseThrow().getState());
        assertEquals("PENDING", jobs.findById(recent.getId()).orElseThrow().getState());
        assertEquals("SENT", jobs.findById(completed.getId()).orElseThrow().getState());
        assertFalse(tokens.existsById(AccountActionService.hash(expired)));
        actions.verifyEmail(valid);
        assertTrue(users.findById(user.getId()).orElseThrow().isEmailVerified());
        assertEquals(0, requests.countByEmailHashAndCreatedAtAfter(oldHash, OffsetDateTime.now().minusDays(1)));
        assertEquals(1, requests.countByEmailHashAndCreatedAtAfter(recentHash, OffsetDateTime.now().minusDays(1)));
    }
}
