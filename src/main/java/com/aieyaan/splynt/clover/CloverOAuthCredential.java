package com.aieyaan.splynt.clover;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "clover_oauth_credentials")
public class CloverOAuthCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", unique = true)
    private Long storeId;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "last_sync_error", length = 255)
    private String lastSyncError;

    public Long getStoreId() { return storeId; }
    public void assignStore(Long storeId) { this.storeId = java.util.Objects.requireNonNull(storeId); }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public String getLastSyncError() { return lastSyncError; }
    public void markSynchronized() { lastSyncedAt = OffsetDateTime.now(); lastSyncError = null; }
    public void markSyncFailed(String error) { lastSyncError = error; }


    @Column(
            name = "merchant_id",
            nullable = false,
            unique = true,
            length = 64
    )
    private String merchantId;

    @Column(
            name = "access_token",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String accessToken;

    @Column(
            name = "refresh_token",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String refreshToken;

    @Column(name = "access_token_expires_at")
    private OffsetDateTime accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at")
    private OffsetDateTime refreshTokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CloverOAuthCredential() {
        // Required by JPA.
    }

    public CloverOAuthCredential(
            String merchantId,
            String accessToken,
            String refreshToken,
            OffsetDateTime accessTokenExpiresAt,
            OffsetDateTime refreshTokenExpiresAt) {

        this.merchantId = requireText(
                merchantId,
                "Clover merchant ID"
        );
        this.accessToken = requireText(
                accessToken,
                "Clover access token"
        );
        this.refreshToken = requireText(
                refreshToken,
                "Clover refresh token"
        );
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    public void rotateTokens(
            String accessToken,
            String refreshToken,
            OffsetDateTime accessTokenExpiresAt,
            OffsetDateTime refreshTokenExpiresAt) {

        this.accessToken = requireText(
                accessToken,
                "Clover access token"
        );
        this.refreshToken = requireText(
                refreshToken,
                "Clover refresh token"
        );
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    @PrePersist
    void beforeInsert() {
        OffsetDateTime now = OffsetDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    private static String requireText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank"
            );
        }

        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public OffsetDateTime getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public OffsetDateTime getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
