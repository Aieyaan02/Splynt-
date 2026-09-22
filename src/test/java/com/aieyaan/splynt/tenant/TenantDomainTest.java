package com.aieyaan.splynt.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantDomainTest {

    @Test
    void newOrganizationStartsWithFreeTrialSubscription() {
        Organization organization =
                new Organization(
                        " Acme Markets ",
                        "acme-markets"
                );

        assertEquals(
                "Acme Markets",
                organization.getName()
        );

        assertEquals(
                "acme-markets",
                organization.getSlug()
        );

        assertEquals(
                SubscriptionPlan.FREE,
                organization.getSubscriptionPlan()
        );

        assertEquals(
                SubscriptionStatus.TRIALING,
                organization.getSubscriptionStatus()
        );

        assertTrue(organization.isActive());
    }

    @Test
    void newStoreUsesDefaultRegionalSettings() {
        Organization organization =
                new Organization(
                        "Acme Markets",
                        "acme-markets"
                );

        Store store = new Store(
                organization,
                " Downtown Store ",
                "downtown"
        );

        assertEquals(
                organization,
                store.getOrganization()
        );

        assertEquals(
                "Downtown Store",
                store.getName()
        );

        assertEquals("US", store.getCountryCode());

        assertEquals(
                "America/New_York",
                store.getTimezone()
        );

        assertEquals("USD", store.getCurrencyCode());
        assertTrue(store.isActive());
    }

    @Test
    void appUserNormalizesEmailAndStartsUnverified() {
        AppUser user = new AppUser(
                " Owner@Example.COM ",
                "$2a$10$examplePasswordHash",
                "Aieyaan",
                "Yeasin"
        );

        assertEquals(
                "owner@example.com",
                user.getEmail()
        );

        assertEquals(
                "Aieyaan Yeasin",
                user.getFullName()
        );

        assertTrue(user.isEnabled());
        assertFalse(user.isEmailVerified());
        assertNull(user.getLastLoginAt());

        user.markEmailVerified();
        user.recordSuccessfulLogin();

        assertTrue(user.isEmailVerified());
        assertTrue(user.getLastLoginAt() != null);
    }

    @Test
    void membershipRoleCanBeChangedAndDeactivated() {
        Organization organization =
                new Organization(
                        "Acme Markets",
                        "acme-markets"
                );

        AppUser user = new AppUser(
                "owner@example.com",
                "$2a$10$examplePasswordHash",
                "Aieyaan",
                "Yeasin"
        );

        OrganizationMembership membership =
                new OrganizationMembership(
                        organization,
                        user,
                        MembershipRole.OWNER
                );

        assertEquals(
                MembershipRole.OWNER,
                membership.getRole()
        );

        assertTrue(membership.isActive());

        membership.changeRole(MembershipRole.ADMIN);
        membership.deactivate();

        assertEquals(
                MembershipRole.ADMIN,
                membership.getRole()
        );

        assertFalse(membership.isActive());
    }
}