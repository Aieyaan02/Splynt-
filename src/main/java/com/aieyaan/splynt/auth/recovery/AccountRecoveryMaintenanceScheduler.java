package com.aieyaan.splynt.auth.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling
@ConditionalOnProperty(name = "splynt.account-maintenance.enabled", havingValue = "true", matchIfMissing = true)
public class AccountRecoveryMaintenanceScheduler {
    private final AccountRecoveryMaintenance maintenance;
    public AccountRecoveryMaintenanceScheduler(AccountRecoveryMaintenance maintenance) { this.maintenance = maintenance; }
    // Cleanup continues even if SMTP is disabled or temporarily unavailable.
    @Scheduled(initialDelay = 60000, fixedDelay = 3600000)
    public void clean() { maintenance.clean(); }
}
