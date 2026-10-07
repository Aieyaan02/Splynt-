package com.aieyaan.splynt.auth.recovery;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import com.aieyaan.splynt.tenant.AppUser;

@Entity @Table(name = "account_email_jobs")
public class AccountEmailJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AccountActionToken.Purpose purpose;
    @Column(nullable = false, length = 255) private String email;
    @Column(name = "credential_version", nullable = false) private long credentialVersion;
    @Column(nullable = false, length = 16) private String state = "PENDING";
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private OffsetDateTime availableAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    protected AccountEmailJob() { }
    AccountEmailJob(AppUser user, AccountActionToken.Purpose purpose) {
        userId = user.getId(); email = user.getEmail(); credentialVersion = user.getCredentialVersion();
        this.purpose = purpose; createdAt = OffsetDateTime.now(); availableAt = createdAt;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public AccountActionToken.Purpose getPurpose() { return purpose; }
    public String getState() { return state; }
    public int getAttempts() { return attempts; }
    boolean ready() { return state.equals("PENDING") && !availableAt.isAfter(OffsetDateTime.now()); }
    boolean validFor(AppUser user) {
        return user.isEnabled() && email.equals(user.getEmail()) && credentialVersion == user.getCredentialVersion()
                && createdAt.isAfter(OffsetDateTime.now().minusHours(1))
                && !(purpose == AccountActionToken.Purpose.VERIFY_EMAIL && user.isEmailVerified());
    }
    void complete(String state) { this.state = state; }
    void failed() {
        if (!state.equals("PENDING")) return;
        attempts++;
        if (attempts >= 5 || createdAt.isBefore(OffsetDateTime.now().minusHours(1))) state = "FAILED";
        else availableAt = OffsetDateTime.now().plusMinutes(1L << attempts);
    }
}
