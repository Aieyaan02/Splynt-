package com.aieyaan.splynt.auth.recovery;

import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling
@ConditionalOnProperty(name = "splynt.account-email.enabled", havingValue = "true")
public class AccountEmailScheduler {
    private final AccountEmailJobRepository jobs;
    private final AccountEmailQueue queue;
    private final AccountEmailDelivery delivery;
    public AccountEmailScheduler(AccountEmailJobRepository jobs, AccountEmailQueue queue, AccountEmailDelivery delivery) {
        this.jobs = jobs; this.queue = queue; this.delivery = delivery;
    }
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void deliverPending() {
        if (!delivery.configured()) return;
        for (var job : jobs.findTop20ByStateAndAvailableAtLessThanEqualOrderById("PENDING", OffsetDateTime.now())) {
            try { queue.deliver(job.getId()); }
            catch (RuntimeException failure) {
                queue.markFailed(job.getId());
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Account email delivery failed for job {}", job.getId());
            }
        }
    }
}
