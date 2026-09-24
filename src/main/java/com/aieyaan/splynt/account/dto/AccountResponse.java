package com.aieyaan.splynt.account.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AccountResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        boolean emailVerified,
        OffsetDateTime lastLoginAt,
        List<OrganizationAccessResponse> organizations) {
}