package com.aieyaan.splynt.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TenantRepositoryTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationMembershipRepository
            membershipRepository;

    @Test
    void findsDemoOrganizationAndStoreCreatedByMigration() {
        Organization organization = organizationRepository
                .findBySlug("splynt-demo")
                .orElseThrow();

        List<Store> stores = storeRepository
                .findAllByOrganizationIdAndActiveTrueOrderByNameAsc(
                        organization.getId()
                );

        assertEquals(
                "Splynt Demo Organization",
                organization.getName()
        );

        assertEquals(1, stores.size());
        assertEquals("Demo Store", stores.getFirst().getName());
        assertEquals("demo-store", stores.getFirst().getSlug());
    }

    @Test
    void savesOrganizationStoreUserAndOwnerMembership() {
        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Fresh Market",
                                "fresh-market-test"
                        )
                );

        Store store = storeRepository.saveAndFlush(
                new Store(
                        organization,
                        "Miami Beach Store",
                        "miami-beach"
                )
        );

        AppUser user = appUserRepository.saveAndFlush(
                new AppUser(
                        " Owner@FreshMarket.com ",
                        "$2a$10$examplePasswordHash",
                        "Aieyaan",
                        "Yeasin"
                )
        );

        OrganizationMembership membership =
                membershipRepository.saveAndFlush(
                        new OrganizationMembership(
                                organization,
                                user,
                                MembershipRole.OWNER
                        )
                );

        Optional<OrganizationMembership> membershipResult =
                membershipRepository
                        .findByOrganizationIdAndUserIdAndActiveTrue(
                                organization.getId(),
                                user.getId()
                        );

        List<OrganizationMembership> userMemberships =
                membershipRepository
                        .findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(
                                user.getId()
                        );

        Optional<Store> storeResult =
                storeRepository.findByIdAndOrganizationId(
                        store.getId(),
                        organization.getId()
                );

        Optional<AppUser> userResult =
                appUserRepository.findByEmailIgnoreCase(
                        "OWNER@FRESHMARKET.COM"
                );

        assertTrue(membershipResult.isPresent());
        assertEquals(
                MembershipRole.OWNER,
                membershipResult.get().getRole()
        );

        assertEquals(1, userMemberships.size());
        assertEquals(membership.getId(), userMemberships.getFirst().getId());

        assertTrue(storeResult.isPresent());
        assertEquals(
                "Miami Beach Store",
                storeResult.get().getName()
        );

        assertTrue(userResult.isPresent());
        assertEquals(
                "owner@freshmarket.com",
                userResult.get().getEmail()
        );
    }

    @Test
    void duplicateNormalizedEmailIsRejected() {
        AppUser firstUser = new AppUser(
                "duplicate@example.com",
                "$2a$10$firstPasswordHash",
                "First",
                "User"
        );

        AppUser duplicateUser = new AppUser(
                "DUPLICATE@EXAMPLE.COM",
                "$2a$10$secondPasswordHash",
                "Second",
                "User"
        );

        appUserRepository.saveAndFlush(firstUser);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> appUserRepository.saveAndFlush(duplicateUser)
        );
    }

    @Test
    void duplicateOrganizationMembershipIsRejected() {
        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Membership Test",
                                "membership-test"
                        )
                );

        AppUser user = appUserRepository.saveAndFlush(
                new AppUser(
                        "membership@example.com",
                        "$2a$10$examplePasswordHash",
                        "Membership",
                        "Tester"
                )
        );

        OrganizationMembership firstMembership =
                new OrganizationMembership(
                        organization,
                        user,
                        MembershipRole.OWNER
                );

        OrganizationMembership duplicateMembership =
                new OrganizationMembership(
                        organization,
                        user,
                        MembershipRole.EMPLOYEE
                );

        membershipRepository.saveAndFlush(firstMembership);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> membershipRepository.saveAndFlush(
                        duplicateMembership
                )
        );
    }

    @Test
    void inactiveMembershipIsNotReturnedAsAuthorized() {
        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Inactive Membership Test",
                                "inactive-membership-test"
                        )
                );

        AppUser user = appUserRepository.saveAndFlush(
                new AppUser(
                        "inactive@example.com",
                        "$2a$10$examplePasswordHash",
                        "Inactive",
                        "Member"
                )
        );

        OrganizationMembership membership =
                new OrganizationMembership(
                        organization,
                        user,
                        MembershipRole.EMPLOYEE
                );

        membership.deactivate();
        membershipRepository.saveAndFlush(membership);

        Optional<OrganizationMembership> result =
                membershipRepository
                        .findByOrganizationIdAndUserIdAndActiveTrue(
                                organization.getId(),
                                user.getId()
                        );

        assertFalse(result.isPresent());
    }
}