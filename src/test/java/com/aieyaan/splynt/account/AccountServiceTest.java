package com.aieyaan.splynt.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.aieyaan.splynt.account.dto.AccountResponse;
import com.aieyaan.splynt.tenant.AppUser;
import com.aieyaan.splynt.tenant.AppUserRepository;
import com.aieyaan.splynt.tenant.MembershipRole;
import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationMembership;
import com.aieyaan.splynt.tenant.OrganizationMembershipRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;
import com.aieyaan.splynt.tenant.SubscriptionPlan;
import com.aieyaan.splynt.tenant.SubscriptionStatus;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OrganizationMembershipRepository
            membershipRepository;

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void returnsCurrentUserOrganizationsAndStores() {
        AppUser user = mock(AppUser.class);
        Organization organization =
                mock(Organization.class);
        OrganizationMembership membership =
                mock(OrganizationMembership.class);
        Store store = mock(Store.class);

        OffsetDateTime lastLoginAt =
                OffsetDateTime.parse(
                        "2026-09-24T00:30:00-04:00"
                );

        when(appUserRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(user.isEnabled()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail())
                .thenReturn("owner@example.com");
        when(user.getFirstName()).thenReturn("Store");
        when(user.getLastName()).thenReturn("Owner");
        when(user.getFullName())
                .thenReturn("Store Owner");
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getLastLoginAt())
                .thenReturn(lastLoginAt);

        when(membershipRepository
                .findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(
                        1L
                ))
                .thenReturn(List.of(membership));

        when(membership.getOrganization())
                .thenReturn(organization);
        when(membership.getRole())
                .thenReturn(MembershipRole.OWNER);

        when(organization.isActive()).thenReturn(true);
        when(organization.getId()).thenReturn(2L);
        when(organization.getName())
                .thenReturn("Example Company");
        when(organization.getSlug())
                .thenReturn("example-company");
        when(organization.getSubscriptionPlan())
                .thenReturn(SubscriptionPlan.FREE);
        when(organization.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.TRIALING);
        when(organization.getTrialEndsAt())
                .thenReturn(null);

        when(storeRepository
                .findAllByOrganizationIdAndActiveTrueOrderByNameAsc(
                        2L
                ))
                .thenReturn(List.of(store));

        when(store.getId()).thenReturn(10L);
        when(store.getName())
                .thenReturn("Example Store");
        when(store.getSlug())
                .thenReturn("example-store");
        when(store.getTimezone())
                .thenReturn("America/New_York");
        when(store.getCurrencyCode())
                .thenReturn("USD");
        when(store.getCountryCode())
                .thenReturn("US");

        AccountResponse response =
                accountService.getCurrentAccount("1");

        assertEquals(1L, response.id());
        assertEquals(
                "owner@example.com",
                response.email()
        );
        assertEquals(1, response.organizations().size());

        assertEquals(
                MembershipRole.OWNER,
                response.organizations().getFirst().role()
        );

        assertEquals(
                10L,
                response.organizations()
                        .getFirst()
                        .stores()
                        .getFirst()
                        .id()
        );
    }

    @Test
    void rejectsInvalidUserIdClaim() {
        assertThrows(
                AccessDeniedException.class,
                () -> accountService
                        .getCurrentAccount("invalid")
        );
    }

    @Test
    void rejectsMissingOrDisabledUser() {
        when(appUserRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> accountService
                        .getCurrentAccount("99")
        );
    }
}