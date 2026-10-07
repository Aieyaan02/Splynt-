package com.aieyaan.splynt.auth.recovery;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRecoveryMaintenance {
    private final AccountEmailJobRepository jobs;
    private final AccountActionTokenRepository tokens;
    private final AccountEmailRequestRepository requests;
    public AccountRecoveryMaintenance(AccountEmailJobRepository jobs, AccountActionTokenRepository tokens, AccountEmailRequestRepository requests) {
        this.jobs = jobs; this.tokens = tokens; this.requests = requests;
    }
    @Transactional
    public Result clean() {
        var now = OffsetDateTime.now();
        // Match delivery's job-before-token lock order. Keep recent completed jobs for diagnosis.
        int removedJobs = jobs.deleteOld(now.minusDays(7));
        int cancelledJobs = jobs.cancelExpired(now.minusHours(1));
        int removedTokens = tokens.deleteExpired(now);
        int removedRequests = requests.deleteExpired(now.minusHours(1));
        return new Result(removedJobs, cancelledJobs, removedTokens, removedRequests);
    }
    public record Result(int removedJobs, int cancelledJobs, int removedTokens, int removedRequests) { }
}
