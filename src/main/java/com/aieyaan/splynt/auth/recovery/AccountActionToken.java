package com.aieyaan.splynt.auth.recovery;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import com.aieyaan.splynt.tenant.AppUser;

@Entity
@Table(name = "account_action_tokens")
public class AccountActionToken {
    public enum Purpose { RESET_PASSWORD, VERIFY_EMAIL }
    @Id @Column(name = "token_hash", length = 64) private String tokenHash;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Purpose purpose;
    @Column(nullable = false, length = 255) private String email;
    @Column(name = "credential_version", nullable = false) private long credentialVersion;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    protected AccountActionToken() { }
    AccountActionToken(String hash, AppUser user, Purpose purpose, OffsetDateTime expiresAt) {
        this.tokenHash = hash; this.userId = user.getId(); this.purpose = purpose;
        this.email = user.getEmail(); this.credentialVersion = user.getCredentialVersion(); this.expiresAt = expiresAt;
    }
    public Long getUserId() { return userId; }
    public boolean validFor(AppUser user, Purpose expected, OffsetDateTime now) {
        return purpose == expected && user.isEnabled() && userId.equals(user.getId())
                && email.equals(user.getEmail()) && credentialVersion == user.getCredentialVersion()
                && expiresAt.isAfter(now);
    }
}
