package com.aieyaan.splynt.contact;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.aieyaan.splynt.tenant.*;
class ContactAccessTest {
    AppUserRepository users = mock(AppUserRepository.class);
    ContactAccess access = new ContactAccess(users, "operator@example.com");
    AppUser user = new AppUser("operator@example.com", "hash", "Operator", "User");
    @Test void onlyVerifiedEnabledAllowlistedOperatorMayReadInquiries() {
        var auth = new UsernamePasswordAuthenticationToken("7", "", java.util.List.of());
        when(users.findById(7L)).thenReturn(Optional.of(user));
        assertFalse(access.allowed(auth), "Anyone can type an operator email into registration; this is not proof of ownership");
        user.markEmailVerified();
        assertTrue(access.allowed(auth));
        user.disable();
        assertFalse(access.allowed(auth));
    }
    @Test void verifiedTenantOwnerIsNotAutomaticallyAPlatformOperator() {
        user.changeEmail("retailer@example.com"); user.markEmailVerified();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        assertFalse(access.allowed(new UsernamePasswordAuthenticationToken("7", "", java.util.List.of())));
        assertFalse(access.allowed(null));
    }
}
