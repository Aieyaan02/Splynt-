package com.aieyaan.splynt.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 32)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 32)
    private SubscriptionStatus subscriptionStatus =
            SubscriptionStatus.TRIALING;

    @Column(name = "stripe_customer_id", unique = true, length = 255)
    private String stripeCustomerId;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Organization() {
        // Required by JPA.
    }

    public Organization(String name, String slug) {
        this.name = requireText(name, "Organization name");
        this.slug = requireText(slug, "Organization slug");
        this.subscriptionPlan = SubscriptionPlan.FREE;
        this.subscriptionStatus = SubscriptionStatus.TRIALING;
        this.active = true;
    }

    public void updateDetails(String name, String slug) {
        this.name = requireText(name, "Organization name");
        this.slug = requireText(slug, "Organization slug");
    }

    public void changeSubscription(
            SubscriptionPlan subscriptionPlan,
            SubscriptionStatus subscriptionStatus
    ) {
        this.subscriptionPlan = Objects.requireNonNull(
                subscriptionPlan,
                "Subscription plan is required"
        );

        this.subscriptionStatus = Objects.requireNonNull(
                subscriptionStatus,
                "Subscription status is required"
        );
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public void setTrialEndsAt(OffsetDateTime trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
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

    private static String requireText(String value, String fieldName) {
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

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public OffsetDateTime getTrialEndsAt() {
        return trialEndsAt;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}