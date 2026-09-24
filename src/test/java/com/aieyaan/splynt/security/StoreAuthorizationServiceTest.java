package com.aieyaan.splynt.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationMembership;
import com.aieyaan.splynt.tenant.OrganizationMembershipRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

@ExtendWith(MockitoExtension.class)
class StoreAuthorizationServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private OrganizationMembershipRepository
            membershipRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private StoreAuthorizationService authorizationService;

    @Test
    void allowsActiveMemberToAccessActiveStore() {
        Store store = mock(Store.class);
        Organization organization =
                mock(Organization.class);

        OrganizationMembership membership =
                mock(OrganizationMembership.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("7");

        when(storeRepository.findById(10L))
                .thenReturn(Optional.of(store));

        when(store.isActive())
                .thenReturn(true);

        when(store.getOrganization())
                .thenReturn(organization);

        when(organization.getId())
                .thenReturn(3L);

        when(membershipRepository
                .findByOrganizationIdAndUserIdAndActiveTrue(
                        3L,
                        7L
                ))
                .thenReturn(Optional.of(membership));

        boolean allowed = authorizationService.canAccess(
                authentication,
                10L
        );

        assertTrue(allowed);
    }

    @Test
    void deniesUserWithoutActiveMembership() {
        Store store = mock(Store.class);
        Organization organization =
                mock(Organization.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("7");

        when(storeRepository.findById(10L))
                .thenReturn(Optional.of(store));

        when(store.isActive())
                .thenReturn(true);

        when(store.getOrganization())
                .thenReturn(organization);

        when(organization.getId())
                .thenReturn(3L);

        when(membershipRepository
                .findByOrganizationIdAndUserIdAndActiveTrue(
                        3L,
                        7L
                ))
                .thenReturn(Optional.empty());

        boolean allowed = authorizationService.canAccess(
                authentication,
                10L
        );

        assertFalse(allowed);
    }

    @Test
    void deniesAccessToInactiveStore() {
        Store store = mock(Store.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("7");

        when(storeRepository.findById(10L))
                .thenReturn(Optional.of(store));

        when(store.isActive())
                .thenReturn(false);

        boolean allowed = authorizationService.canAccess(
                authentication,
                10L
        );

        assertFalse(allowed);
    }

    @Test
    void deniesInvalidJwtSubject() {
        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("not-a-user-id");

        boolean allowed = authorizationService.canAccess(
                authentication,
                10L
        );

        assertFalse(allowed);
    }

    @Test
    void deniesMissingAuthentication() {
        boolean allowed = authorizationService.canAccess(
                null,
                10L
        );

        assertFalse(allowed);
    }
}