package com.aieyaan.splynt.auth.dto;

import com.aieyaan.splynt.tenant.MembershipRole;

public record RegisterResponse(
        Long userId,
        String email,
        String firstName,
        String lastName,
        Long organizationId,
        String organizationName,
        String organizationSlug,
        Long storeId,
        String storeName,
        String storeSlug,
        MembershipRole role
) {
}