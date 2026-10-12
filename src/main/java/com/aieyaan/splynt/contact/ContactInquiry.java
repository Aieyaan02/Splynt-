package com.aieyaan.splynt.contact;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
@Entity @Table(name = "contact_inquiries")
public class ContactInquiry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 255) private String email;
    @Column(length = 150) private String business;
    @Column(nullable = false, length = 3000) private String message;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "notification_state", nullable = false, length = 16) private String notificationState = "PENDING";
    @Column(name = "notification_attempts", nullable = false) private int notificationAttempts;
    @Column(name = "notification_available_at", nullable = false) private OffsetDateTime notificationAvailableAt = OffsetDateTime.now();
    public String getNotificationState() { return notificationState; }
    public int getNotificationAttempts() { return notificationAttempts; }
    public OffsetDateTime getNotificationAvailableAt() { return notificationAvailableAt; }
    boolean notificationReady(OffsetDateTime now) { return "PENDING".equals(notificationState) && !notificationAvailableAt.isAfter(now); }
    void notificationAccepted() { notificationAttempts++; notificationState = "ACCEPTED"; }
    void notificationFailed(OffsetDateTime now) {
        notificationAttempts++;
        if (notificationAttempts >= 5) notificationState = "FAILED";
        else notificationAvailableAt = now.plusMinutes(1L << notificationAttempts);
    }
    protected ContactInquiry() {}
    public ContactInquiry(String name, String email, String business, String message) {
        this.name = name.trim(); this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.business = business == null ? null : business.trim(); this.message = message.trim();
        this.createdAt = OffsetDateTime.now();
    }
    public String getName() { return name; }
    public String getBusiness() { return business; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getMessage() { return message; }
}
