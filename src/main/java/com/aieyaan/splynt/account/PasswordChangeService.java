package com.aieyaan.splynt.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import com.aieyaan.splynt.tenant.AppUserRepository;
import com.aieyaan.splynt.auth.dto.ChangePasswordRequest;
import com.aieyaan.splynt.security.AccountTokenValidator;

@Service
public class PasswordChangeService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    public PasswordChangeService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    @Transactional
    public void change(Jwt jwt, ChangePasswordRequest request) {
        var user = users.findLockedById(Long.valueOf(jwt.getSubject()))
                .filter(u -> u.isEnabled() && u.getCredentialVersion() == AccountTokenValidator.credentialVersion(jwt))
                .orElseThrow(() -> new AccessDeniedException("Sign in again to continue"));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Current password is incorrect.");
        if (request.currentPassword().equals(request.newPassword()))
            throw new IllegalArgumentException("Choose a new password different from your current password.");
        user.changePasswordHash(encoder.encode(request.newPassword()));
        users.flush();
    }
}
