package com.aieyaan.splynt.auth;

import com.aieyaan.splynt.auth.dto.LoginRequest;
import com.aieyaan.splynt.auth.dto.LoginResponse;
import com.aieyaan.splynt.auth.dto.RegisterRequest;
import com.aieyaan.splynt.auth.exception.InvalidCredentialsException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class LoginServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void validCredentialsReturnSignedJwt() {
        authService.register(
                registrationRequest(
                        "jwt-owner@example.com",
                        "jwt-login-organization"
                )
        );

        LoginResponse response = authService.login(
                new LoginRequest(
                        "JWT-OWNER@EXAMPLE.COM",
                        "SecurePassword123"
                )
        );

        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresInSeconds());
        assertFalse(response.accessToken().isBlank());

        Jwt jwt = jwtDecoder.decode(response.accessToken());

        assertEquals(
                response.userId().toString(),
                jwt.getSubject()
        );

        assertEquals(
                "jwt-owner@example.com",
                jwt.getClaimAsString("email")
        );

        assertEquals(
               "https://api.splynt.local",
                jwt.getIssuer().toString()
        );

        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
    }

    @Test
    void incorrectPasswordIsRejected() {
        authService.register(
                registrationRequest(
                        "wrong-password@example.com",
                        "wrong-password-organization"
                )
        );

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(
                        new LoginRequest(
                                "wrong-password@example.com",
                                "IncorrectPassword123"
                        )
                )
        );
    }

    @Test
    void unknownEmailIsRejected() {
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(
                        new LoginRequest(
                                "missing-user@example.com",
                                "SecurePassword123"
                        )
                )
        );
    }

    private RegisterRequest registrationRequest(
            String email,
            String organizationSlug
    ) {
        return new RegisterRequest(
                email,
                "SecurePassword123",
                "JWT",
                "Owner",
                "JWT Test Organization",
                organizationSlug,
                "JWT Test Store",
                "jwt-test-store"
        );
    }
}