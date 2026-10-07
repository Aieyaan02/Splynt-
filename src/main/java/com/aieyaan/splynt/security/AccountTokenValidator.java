package com.aieyaan.splynt.security;

import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import com.aieyaan.splynt.tenant.AppUserRepository;

@Component
public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final AppUserRepository users;
    public AccountTokenValidator(AppUserRepository users) { this.users = users; }
    public static long credentialVersion(Jwt jwt) {
        Object value = jwt.getClaims().get("credential_version");
        // Existing signed tokens predate credential versioning and belong to version zero.
        if (value == null) return 0;
        if (!(value instanceof Number number)) return -1;
        return number.longValue();
    }
    @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (users.findById(id).filter(u -> u.isEnabled()
                    && u.getCredentialVersion() == credentialVersion(jwt)).isPresent())
                return OAuth2TokenValidatorResult.success();
        } catch (NumberFormatException ignored) { }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sign in again to continue", null));
    }
}
