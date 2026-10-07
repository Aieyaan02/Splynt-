package com.aieyaan.splynt.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.tenant.OrganizationMembershipRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

@Service
@Transactional(readOnly = true)
public class StoreAuthorizationService {

    private final StoreRepository storeRepository;
    private final com.aieyaan.splynt.tenant.AppUserRepository users;
    private final OrganizationMembershipRepository
            membershipRepository;

    public StoreAuthorizationService(
            StoreRepository storeRepository,
            OrganizationMembershipRepository membershipRepository, com.aieyaan.splynt.tenant.AppUserRepository users) {

        this.storeRepository = storeRepository;
        this.users = users;
        this.membershipRepository = membershipRepository;
    }

    public boolean canAccess(
            Authentication authentication,
            Long storeId) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || storeId == null) {

            return false;
        }

        Long userId = extractUserId(authentication);

        if (userId == null || users.findById(userId).filter(com.aieyaan.splynt.tenant.AppUser::isEnabled).isEmpty()) {
            return false;
        }

        Optional<Store> storeResult =
                storeRepository.findById(storeId);

        if (storeResult.isEmpty()) {
            return false;
        }

        Store store = storeResult.get();

        if (!store.isActive() || !store.getOrganization().isActive()) {
            return false;
        }

        Long organizationId =
                store.getOrganization().getId();

        return membershipRepository
                .findByOrganizationIdAndUserIdAndActiveTrue(
                        organizationId,
                        userId
                )
                .isPresent();
    }

    public boolean canManage(Authentication authentication, Long storeId) {
        return authentication != null && authentication.isAuthenticated()
                && canManageUser(extractUserId(authentication), storeId);
    }

    public boolean canManageUser(Long userId, Long storeId) {
        if (userId == null || storeId == null || users.findById(userId).filter(com.aieyaan.splynt.tenant.AppUser::isEnabled).isEmpty()) return false;
        return storeRepository.findById(storeId).filter(Store::isActive).filter(s -> s.getOrganization().isActive())
                .flatMap(store -> membershipRepository.findByOrganizationIdAndUserIdAndActiveTrue(
                        store.getOrganization().getId(), userId))
                .map(membership -> membership.getRole() == com.aieyaan.splynt.tenant.MembershipRole.OWNER
                        || membership.getRole() == com.aieyaan.splynt.tenant.MembershipRole.ADMIN)
                .orElse(false);
    }

    private Long extractUserId(
            Authentication authentication) {

        try {
            Long userId = Long.valueOf(
                    authentication.getName()
            );

            if (userId <= 0) {
                return null;
            }

            return userId;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}