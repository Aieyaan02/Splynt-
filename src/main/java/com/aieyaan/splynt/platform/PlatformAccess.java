package com.aieyaan.splynt.platform;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.aieyaan.splynt.security.AccountTokenValidator;

@Component
public class PlatformAccess {
    private final JdbcTemplate db;
    public PlatformAccess(JdbcTemplate db) { this.db = db; }
    public boolean allowed(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) return false;
        try {
            Long count = db.queryForObject("""
                select count(*) from platform_admin a join app_users u on u.id=a.user_id
                where a.singleton=1 and u.id=? and u.enabled=true and u.email_verified=true and u.credential_version=?
                """, Long.class, Long.parseLong(jwt.getName()), AccountTokenValidator.credentialVersion(jwt.getToken()));
            return count != null && count == 1;
        } catch (NumberFormatException | org.springframework.dao.DataAccessException ex) { return false; }
    }
}
