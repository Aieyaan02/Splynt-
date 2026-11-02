package com.aieyaan.splynt.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.aieyaan.splynt.security.AccountTokenValidator;
import com.aieyaan.splynt.tenant.AppUserRepository;

/** Explicit owner-confirmed deletion; shared user accounts are preserved. */
@RestController
@RequestMapping("/api/me")
public class AccountDeletionController {
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final com.aieyaan.splynt.security.AuthenticationLimits limits;
    public AccountDeletionController(JdbcTemplate db,AppUserRepository users,PasswordEncoder passwords,com.aieyaan.splynt.security.AuthenticationLimits limits) {
        this.db=db; this.users=users; this.passwords=passwords; this.limits=limits;
    }
    public record Confirmation(@NotBlank @Size(max=200) String password,
            @NotBlank @Size(max=255) String confirmation) {}
    private Long verify(Jwt jwt, String password) {
        limits.check("account-deletion",jwt.getSubject(),10,15);
        Long id=Long.valueOf(jwt.getSubject());
        var user=users.findLockedById(id).filter(u->u.isEnabled() && u.getCredentialVersion()==AccountTokenValidator.credentialVersion(jwt))
                .orElseThrow(()->new AccessDeniedException("Sign in again to continue"));
        if (!passwords.matches(password,user.getPasswordHash())) throw new AccessDeniedException("Identity confirmation failed");
        return id;
    }
    @DeleteMapping("/organizations/{organizationId}")
    @Transactional
    public void deleteOrganization(@AuthenticationPrincipal Jwt jwt,@PathVariable Long organizationId,
            @Valid @RequestBody Confirmation request) {
        Long userId=verify(jwt,request.password());
        var names=db.queryForList("SELECT slug FROM organizations WHERE id=? FOR UPDATE",String.class,organizationId);
        if(names.isEmpty() || db.queryForObject("SELECT count(*) FROM organization_memberships WHERE organization_id=? AND user_id=? AND active=true AND role='OWNER'",Long.class,organizationId,userId)!=1)
            throw new AccessDeniedException("Only the organization owner can delete this workspace");
        if(!names.getFirst().equals(request.confirmation())) throw new IllegalArgumentException("Enter the exact workspace slug to confirm deletion");
        var storeIds=db.queryForList("SELECT id FROM stores WHERE organization_id=? ORDER BY id FOR UPDATE",Long.class,organizationId);
        for(Long storeId:storeIds) {
            db.update("DELETE FROM clover_oauth_attempts WHERE store_id=?",storeId);
            db.update("DELETE FROM clover_oauth_credentials WHERE store_id=?",storeId);
            db.update("DELETE FROM store_ai_reports WHERE store_id=?",storeId);
            db.update("DELETE FROM sales_events WHERE store_id=?",storeId);
            db.update("DELETE FROM inventory_movements WHERE product_id IN (SELECT id FROM products WHERE store_id=?)",storeId);
            db.update("DELETE FROM products WHERE store_id=?",storeId);
        }
        db.update("DELETE FROM stores WHERE organization_id=?",organizationId);
        db.update("DELETE FROM organization_memberships WHERE organization_id=?",organizationId);
        db.update("DELETE FROM organizations WHERE id=?",organizationId);
    }
    @DeleteMapping
    @Transactional
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Confirmation request) {
        Long userId=verify(jwt,request.password());
        var user=users.findById(userId).orElseThrow();
        if(!user.getEmail().equalsIgnoreCase(request.confirmation())) throw new IllegalArgumentException("Enter your email to confirm deletion");
        if(db.queryForObject("SELECT count(*) FROM platform_admin WHERE user_id=?",Long.class,userId)>0)
            throw new IllegalArgumentException("The platform operator account requires a separate administrative handover");
        if(db.queryForObject("SELECT count(*) FROM organization_memberships WHERE user_id=? AND role='OWNER'",Long.class,userId)>0)
            throw new IllegalArgumentException("Delete or transfer owned workspaces before deleting your account");
        db.update("DELETE FROM clover_oauth_attempts WHERE user_id=?",userId);
        db.update("DELETE FROM organization_memberships WHERE user_id=?",userId);
        db.update("DELETE FROM account_email_jobs WHERE user_id=?",userId);
        db.update("DELETE FROM account_action_tokens WHERE user_id=?",userId);
        db.update("DELETE FROM platform_recovery_code WHERE user_id=?",userId);
        db.update("DELETE FROM platform_mfa WHERE user_id=?",userId);
        db.update("UPDATE security_audit SET actor_id=NULL WHERE actor_id=?",userId);
        // Public inquiries have unverified sender emails; handle those through verified support requests.
        users.delete(user); users.flush();
    }
}
