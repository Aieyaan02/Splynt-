package com.aieyaan.splynt.auth.recovery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.tenant.*;

@Service
public class AccountEmailQueue {
    private final AccountEmailJobRepository jobs;
    private final AppUserRepository users;
    private final AccountActionService actions;
    private final AccountEmailDelivery delivery;
    public AccountEmailQueue(AccountEmailJobRepository jobs, AppUserRepository users, AccountActionService actions, AccountEmailDelivery delivery) {
        this.jobs = jobs; this.users = users; this.actions = actions; this.delivery = delivery;
    }
    @Transactional
    public void enqueue(Long userId, AccountActionToken.Purpose purpose) {
        var user = users.findLockedById(userId).filter(AppUser::isEnabled).orElseThrow();
        if (jobs.findFirstByUserIdAndPurposeAndStateOrderByIdDesc(userId, purpose, "PENDING").isEmpty())
            jobs.saveAndFlush(new AccountEmailJob(user, purpose));
    }
    @Transactional
    public void deliver(Long id) {
        var job = jobs.findLocked(id).orElse(null);
        if (job == null || !job.ready()) return;
        var user = users.findLockedById(job.getUserId()).orElse(null);
        if (user == null || !job.validFor(user)) { job.complete("CANCELLED"); return; }
        // Token creation and job completion commit together; raw tokens exist only in memory and the email.
        String token = actions.issue(user.getId(), job.getPurpose());
        delivery.send(job.getEmail(), job.getPurpose(), token);
        job.complete("SENT");
    }
    @Transactional
    public void markFailed(Long id) { jobs.findLocked(id).ifPresent(AccountEmailJob::failed); }
}
