package com.aieyaan.splynt.auth.recovery;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name = "account_email_requests")
public class AccountEmailRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "email_hash", nullable = false, length = 64) private String emailHash;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    protected AccountEmailRequest() { }
    AccountEmailRequest(String emailHash, OffsetDateTime createdAt) { this.emailHash = emailHash; this.createdAt = createdAt; }
}
