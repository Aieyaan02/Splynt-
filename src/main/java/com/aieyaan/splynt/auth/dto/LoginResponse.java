package com.aieyaan.splynt.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        Long userId,
        String email,
        String firstName,
        String lastName
) {
}