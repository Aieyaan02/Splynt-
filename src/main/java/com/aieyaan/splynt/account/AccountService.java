package com.aieyaan.splynt.account;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.account.dto.AccountResponse;
import com.aieyaan.splynt.account.dto.OrganizationAccessResponse;
import com.aieyaan.splynt.account.dto.StoreSummaryResponse;
import com.aieyaan.splynt.tenant.AppUser;
import com.aieyaan.splynt.tenant.AppUserRepository;
import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationMembership;
import com.aieyaan.splynt.tenant.OrganizationMembershipRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AppUserRepository appUserRepository;
    private final OrganizationMembershipRepository
            membershipRepository;
    private final StoreRepository storeRepository;

    public AccountService(
            AppUserRepository appUserRepository,
            OrganizationMembershipRepository
                    membershipRepository,
            StoreRepository storeRepository) {

        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.storeRepository = storeRepository;
    }

    public AccountResponse getCurrentAccount(
            String userIdClaim) {

        Long userId = parseUserId(userIdClaim);

        AppUser user = appUserRepository
                .findById(userId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is not available"
                ));

        List<OrganizationAccessResponse> organizations =
                membershipRepository
                        .findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(
                                userId
                        )
                        .stream()
                        .filter(membership ->
                                membership
                                        .getOrganization()
                                        .isActive()
                        )
                        .map(this::toOrganizationResponse)
                        .toList();

        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.isEmailVerified(),
                user.getLastLoginAt(),
                organizations
        );
    }

    private OrganizationAccessResponse
            toOrganizationResponse(
                    OrganizationMembership membership) {

        Organization organization =
                membership.getOrganization();

        List<StoreSummaryResponse> stores =
                storeRepository
                        .findAllByOrganizationIdAndActiveTrueOrderByNameAsc(
                                organization.getId()
                        )
                        .stream()
                        .map(this::toStoreResponse)
                        .toList();

        return new OrganizationAccessResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                membership.getRole(),
                organization.getSubscriptionPlan(),
                organization.getSubscriptionStatus(),
                organization.getTrialEndsAt(),
                stores
        );
    }

    private StoreSummaryResponse toStoreResponse(Store store) {
        return new StoreSummaryResponse(
                store.getId(),
                store.getName(),
                store.getSlug(),
                store.getTimezone(),
                store.getCurrencyCode(),
                store.getCountryCode()
        );
    }

    private Long parseUserId(String userIdClaim) {
        try {
            Long userId = Long.valueOf(userIdClaim);

            if (userId <= 0) {
                throw new NumberFormatException();
            }

            return userId;
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException(
                    "Access token contains an invalid user ID"
            );
        }
    }
}