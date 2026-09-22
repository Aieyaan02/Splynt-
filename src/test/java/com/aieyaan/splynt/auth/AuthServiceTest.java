package com.aieyaan.splynt.auth;

import com.aieyaan.splynt.auth.dto.RegisterRequest;
import com.aieyaan.splynt.auth.dto.RegisterResponse;
import com.aieyaan.splynt.auth.exception.DuplicateEmailException;
import com.aieyaan.splynt.auth.exception.DuplicateOrganizationSlugException;
import com.aieyaan.splynt.tenant.AppUser;
import com.aieyaan.splynt.tenant.AppUserRepository;
import com.aieyaan.splynt.tenant.MembershipRole;
import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationMembership;
import com.aieyaan.splynt.tenant.OrganizationMembershipRepository;
import com.aieyaan.splynt.tenant.OrganizationRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private OrganizationMembershipRepository
            membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationCreatesCompleteSaasAccount() {
        RegisterRequest request = new RegisterRequest(
                " Owner@GrabNGo.com ",
                "SecurePassword123",
                "Aieyaan",
                "Yeasin",
                "Grab n' GO",
                "grab-n-go-auth-test",
                "Miami Beach Store",
                "miami-beach"
        );

        RegisterResponse response =
                authService.register(request);

        AppUser user = appUserRepository
                .findByEmailIgnoreCase("owner@grabngo.com")
                .orElseThrow();

        Organization organization = organizationRepository
                .findBySlug("grab-n-go-auth-test")
                .orElseThrow();

        Store store = storeRepository
                .findByOrganizationIdAndSlug(
                        organization.getId(),
                        "miami-beach"
                )
                .orElseThrow();

        OrganizationMembership membership =
                membershipRepository
                        .findByOrganizationIdAndUserIdAndActiveTrue(
                                organization.getId(),
                                user.getId()
                        )
                        .orElseThrow();

        List<OrganizationMembership> memberships =
                membershipRepository
                        .findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(
                                user.getId()
                        );

        assertEquals(
                "owner@grabngo.com",
                user.getEmail()
        );

        assertNotEquals(
                "SecurePassword123",
                user.getPasswordHash()
        );

        assertTrue(
                passwordEncoder.matches(
                        "SecurePassword123",
                        user.getPasswordHash()
                )
        );

        assertEquals(
                MembershipRole.OWNER,
                membership.getRole()
        );

        assertEquals(1, memberships.size());

        assertEquals(user.getId(), response.userId());
        assertEquals(
                organization.getId(),
                response.organizationId()
        );
        assertEquals(store.getId(), response.storeId());
        assertEquals(
                MembershipRole.OWNER,
                response.role()
        );
    }

    @Test
    void duplicateEmailRegistrationIsRejected() {
        authService.register(
                new RegisterRequest(
                        "duplicate-auth@example.com",
                        "SecurePassword123",
                        "First",
                        "Owner",
                        "First Organization",
                        "first-auth-organization",
                        "First Store",
                        "first-store"
                )
        );

        RegisterRequest duplicateRequest =
                new RegisterRequest(
                        "DUPLICATE-AUTH@EXAMPLE.COM",
                        "AnotherPassword123",
                        "Second",
                        "Owner",
                        "Second Organization",
                        "second-auth-organization",
                        "Second Store",
                        "second-store"
                );

        assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(duplicateRequest)
        );
    }

    @Test
    void duplicateOrganizationSlugIsRejected() {
        authService.register(
                new RegisterRequest(
                        "first-owner@example.com",
                        "SecurePassword123",
                        "First",
                        "Owner",
                        "Shared Organization",
                        "shared-auth-organization",
                        "First Store",
                        "first-store"
                )
        );

        RegisterRequest duplicateRequest =
                new RegisterRequest(
                        "second-owner@example.com",
                        "AnotherPassword123",
                        "Second",
                        "Owner",
                        "Another Organization Name",
                        "shared-auth-organization",
                        "Second Store",
                        "second-store"
                );

        assertThrows(
                DuplicateOrganizationSlugException.class,
                () -> authService.register(duplicateRequest)
        );
    }
}