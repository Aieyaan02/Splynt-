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
    private final com.aieyaan.splynt.insights.CloverSalesSyncService sales;
    public CloverSyncJobs(CloverInventorySyncService sync, CloverOAuthCredentialRepository credentials,
            PlatformTransactionManager transactionManager, com.aieyaan.splynt.insights.CloverSalesSyncService sales) {
        this.sync = sync; this.credentials = credentials; this.sales = sales;
        this.transactions = new TransactionTemplate(transactionManager);
    }
    public CloverSyncResponse run(Long storeId) {
        try {
            CloverSyncResponse result = sync.synchronize(storeId);
            transactions.executeWithoutResult(tx -> credentials.findLockedByStoreId(storeId).ifPresent(connection -> {
                connection.recordInventoryReview(result);
            }));
            try { sales.synchronize(storeId); }
            catch (RuntimeException salesFailure) {
                transactions.executeWithoutResult(tx -> credentials.findLockedByStoreId(storeId)
                        .ifPresent(connection -> connection.markSalesFailed()));
            }
            return result;
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(tx -> credentials.findLockedByStoreId(storeId).ifPresent(connection ->
                    connection.markSyncFailed("Inventory could not be refreshed. Retry, or reconnect Clover if access expired.")));
            throw failure;
        }
    }
}
