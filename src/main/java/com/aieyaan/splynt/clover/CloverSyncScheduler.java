package com.aieyaan.splynt.clover;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "splynt.integrations.clover.background-sync", havingValue = "true", matchIfMissing = true)
public class CloverSyncScheduler {
    private final CloverOAuthCredentialRepository credentials;
    private final CloverSyncJobs jobs;
    public CloverSyncScheduler(CloverOAuthCredentialRepository credentials, CloverSyncJobs jobs) {
        this.credentials = credentials; this.jobs = jobs;
    }
    @Scheduled(initialDelayString = "${splynt.integrations.clover.sync-initial-delay:10000}",
            fixedDelayString = "${splynt.integrations.clover.sync-interval:60000}")
    public void synchronizeConnectedStores() {
        for (var connection : credentials.findAllByStoreIdIsNotNull()) {
            try { jobs.run(connection.getStoreId()); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scheduled Clover sync failed for store {} ({})",
                        connection.getStoreId(), failure.getClass().getSimpleName());
            }
        }
    }
}
