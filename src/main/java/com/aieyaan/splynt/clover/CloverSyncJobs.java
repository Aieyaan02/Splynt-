package com.aieyaan.splynt.clover;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.aieyaan.splynt.clover.dto.CloverSyncResponse;

@Service
public class CloverSyncJobs {
    private final CloverInventorySyncService sync;
    private final CloverOAuthCredentialRepository credentials;
    private final TransactionTemplate transactions;
    public CloverSyncJobs(CloverInventorySyncService sync, CloverOAuthCredentialRepository credentials,
            PlatformTransactionManager transactionManager) {
        this.sync = sync; this.credentials = credentials;
        this.transactions = new TransactionTemplate(transactionManager);
    }
    public CloverSyncResponse run(Long storeId) {
        try {
            CloverSyncResponse result = sync.synchronize(storeId);
            transactions.executeWithoutResult(tx -> credentials.findLockedByStoreId(storeId).ifPresent(connection -> {
                connection.markSynchronized();
                if (result.skipped() > 0) connection.markSyncFailed(result.skipped()
                        + " items skipped: archived, missing stock, or unsupported quantities. Review Clover inventory.");
            }));
            return result;
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(tx -> credentials.findLockedByStoreId(storeId).ifPresent(connection ->
                    connection.markSyncFailed("Inventory could not be refreshed. Retry, or reconnect Clover if access expired.")));
            throw failure;
        }
    }
}
