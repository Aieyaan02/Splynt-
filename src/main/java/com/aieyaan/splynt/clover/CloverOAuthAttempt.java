package com.aieyaan.splynt.clover;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "clover_oauth_attempts")
public class CloverOAuthAttempt {
    @Id @Column(name = "state_hash", length = 64) private String stateHash;
    @Column(name = "browser_hash", nullable = false, length = 64) private String browserHash;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    protected CloverOAuthAttempt() {}
    public CloverOAuthAttempt(String stateHash, String browserHash, Long storeId, Long userId, OffsetDateTime expiresAt) {
        this.stateHash = stateHash; this.browserHash = browserHash; this.storeId = storeId;
        this.userId = userId; this.expiresAt = expiresAt;
    }
    public String getBrowserHash() { return browserHash; }
    public Long getStoreId() { return storeId; }
    public Long getUserId() { return userId; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
