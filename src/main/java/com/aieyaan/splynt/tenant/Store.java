package com.aieyaan.splynt.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "address_line_1", length = 150)
    private String addressLine1;

    @Column(name = "address_line_2", length = 150)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "US";

    @Column(nullable = false, length = 64)
    private String timezone = "America/New_York";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Store() {
        // Required by JPA.
    }

    public Store(
            Organization organization,
            String name,
            String slug
    ) {
        this.organization = Objects.requireNonNull(
                organization,
                "Organization is required"
        );

        this.name = requireText(name, "Store name");
        this.slug = requireText(slug, "Store slug");
        this.countryCode = "US";
        this.timezone = "America/New_York";
        this.currencyCode = "USD";
        this.active = true;
    }

    public void updateDetails(String name, String slug) {
        this.name = requireText(name, "Store name");
        this.slug = requireText(slug, "Store slug");
    }

    public void updateAddress(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String countryCode
    ) {
        this.addressLine1 = trimToNull(addressLine1);
        this.addressLine2 = trimToNull(addressLine2);
        this.city = trimToNull(city);
        this.state = trimToNull(state);
        this.postalCode = trimToNull(postalCode);

        String normalizedCountryCode =
                requireText(countryCode, "Country code").toUpperCase();

        if (normalizedCountryCode.length() != 2) {
            throw new IllegalArgumentException(
                    "Country code must contain exactly 2 characters"
            );
        }

        this.countryCode = normalizedCountryCode;
    }

    public void updateRegionalSettings(
            String timezone,
            String currencyCode
    ) {
        this.timezone = requireText(timezone, "Timezone");

        String normalizedCurrencyCode =
                requireText(currencyCode, "Currency code").toUpperCase();

        if (normalizedCurrencyCode.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency code must contain exactly 3 characters"
            );
        }

        this.currencyCode = normalizedCurrencyCode;
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

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrencyCode() {
        return currencyCode;
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