package com.aieyaan.splynt.auth.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.aieyaan.splynt.auth.recovery.AccountActionToken.Purpose.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest
class AccountEmailQueueTest {
    @Autowired AccountEmailQueue queue;
    @Autowired AccountEmailJobRepository jobs;
    @Autowired AccountActionService actions;
    @Autowired AccountActionTokenRepository tokens;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccountEmailDelivery delivery;
    AppUser user;
    @BeforeEach void setup() { user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", "hash", "Test", "User")); }
    AccountEmailJob pending() { return jobs.findFirstByUserIdAndPurposeAndStateOrderByIdDesc(user.getId(), VERIFY_EMAIL, "PENDING").orElseThrow(); }
    @Test void queuedRequestSurvivesUntilDeliveryAndDoesNotStoreRawToken() {
        queue.enqueue(user.getId(), VERIFY_EMAIL);
        long id = pending().getId();
        queue.enqueue(user.getId(), VERIFY_EMAIL);
        assertEquals(id, pending().getId());
        var link = new AtomicReference<String>();
        doAnswer(call -> { link.set(call.getArgument(2)); return null; }).when(delivery).send(eq(user.getEmail()), eq(VERIFY_EMAIL), anyString());
        queue.deliver(id);
        assertEquals("SENT", jobs.findById(id).orElseThrow().getState());
        actions.verifyEmail(link.get());
        assertTrue(users.findById(user.getId()).orElseThrow().isEmailVerified());
        queue.deliver(id);
        verify(delivery, times(1)).send(anyString(), any(), anyString());
    }
    @Test void failedSendRollsBackTokenAndRetryCanComplete() {
        queue.enqueue(user.getId(), VERIFY_EMAIL);
        long id = pending().getId();
        var failedLink = new AtomicReference<String>();
        doAnswer(call -> { failedLink.set(call.getArgument(2)); throw new IllegalStateException("simulated transport failure"); })
                .when(delivery).send(anyString(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> queue.deliver(id));
        assertFalse(tokens.existsById(AccountActionService.hash(failedLink.get())));
        queue.markFailed(id);
        assertEquals(1, jobs.findById(id).orElseThrow().getAttempts());
        queue.deliver(id); // Backoff has not elapsed.
        verify(delivery, times(1)).send(anyString(), any(), anyString());
        jdbc.update("update account_email_jobs set available_at = ? where id = ?", OffsetDateTime.now().minusSeconds(1), id);
        var sent = new AtomicReference<String>();
        doAnswer(call -> { sent.set(call.getArgument(2)); return null; }).when(delivery).send(anyString(), any(), anyString());
        queue.deliver(id);
        assertEquals("SENT", jobs.findById(id).orElseThrow().getState());
        actions.verifyEmail(sent.get());
    }
    @Test void credentialChangesCancelQueuedMailWithoutSending() {
        queue.enqueue(user.getId(), VERIFY_EMAIL);
        long id = pending().getId();
        var changed = users.findById(user.getId()).orElseThrow(); changed.changePasswordHash("new-hash"); users.saveAndFlush(changed);
        queue.deliver(id);
        assertEquals("CANCELLED", jobs.findById(id).orElseThrow().getState());
        verifyNoInteractions(delivery);
    }
    @Test void expiredRequestsAreCancelledAndRetriesAreBounded() {
        queue.enqueue(user.getId(), VERIFY_EMAIL);
        long id = pending().getId();
        jdbc.update("update account_email_jobs set created_at = ? where id = ?", OffsetDateTime.now().minusHours(2), id);
        queue.deliver(id);
        assertEquals("CANCELLED", jobs.findById(id).orElseThrow().getState());
        queue.enqueue(user.getId(), VERIFY_EMAIL);
        long retryId = pending().getId();
        for (int attempt = 0; attempt < 5; attempt++) queue.markFailed(retryId);
        assertEquals("FAILED", jobs.findById(retryId).orElseThrow().getState());
        verifyNoInteractions(delivery);
    }
}
