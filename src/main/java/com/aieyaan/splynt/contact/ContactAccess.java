package com.aieyaan.splynt.contact;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.aieyaan.splynt.tenant.AppUserRepository;
@Component
public class ContactAccess {
    private final Set<String> operators;
    private final AppUserRepository users;
    public ContactAccess(AppUserRepository users, @Value("${splynt.contact.operator-emails:}") String emails) {
        this.users = users;
        this.operators = new HashSet<>();
        for (String email : emails.split(",")) if (!email.isBlank()) operators.add(email.trim().toLowerCase(Locale.ROOT));
    }
    public boolean allowed(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || operators.isEmpty()) return false;
        try {
            return users.findById(Long.valueOf(authentication.getName()))
                    .filter(user -> user.isEnabled() && user.isEmailVerified())
                    .map(user -> operators.contains(user.getEmail().toLowerCase(Locale.ROOT))).orElse(false);
        } catch (NumberFormatException ex) { return false; }
    }
}
