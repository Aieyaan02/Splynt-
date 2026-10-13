package com.aieyaan.splynt.platform;

import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aieyaan.splynt.tenant.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;

/** Server-operator provisioning only; no public promotion or registration endpoint. */
@Component @ConditionalOnProperty(name="splynt.platform.bootstrap.enabled", havingValue="true")
public class PlatformBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final JdbcTemplate db;
    private final String email;
    private final String hash;
    private final Validator validator;
    public PlatformBootstrap(AppUserRepository users, JdbcTemplate db, Validator validator,
        @Value("${splynt.platform.bootstrap.email:}") String email,
        @Value("${splynt.platform.bootstrap.password-hash:}") String hash) {
        this.users=users; this.db=db; this.email=email; this.hash=hash; this.validator=validator;
    }
    record Provision(@NotBlank @Email @Size(max=255) String email,
        @Pattern(regexp="\\$2[aby]\\$1[234]\\$[./A-Za-z0-9]{53}") String hash) {}
    @Override @Transactional public void run(ApplicationArguments args) {
        if (db.queryForObject("select count(*) from platform_admin",Long.class) > 0) return;
        if (!validator.validate(new Provision(email,hash)).isEmpty()) throw new IllegalStateException("Platform admin provisioning requires a valid private email and bcrypt password hash (cost 12–14).");
        if (users.existsByEmailIgnoreCase(email.trim())) throw new IllegalStateException("Use a separate, unused platform admin email. Existing users are never automatically promoted.");
        AppUser admin = new AppUser(email,hash,"Platform","Admin");
        // Enabling this server-only bootstrap asserts that the operator verified this identity.
        admin.markEmailVerified();
        admin=users.saveAndFlush(admin);
        db.update("insert into platform_admin(singleton,user_id) values (1,?)",admin.getId());
    }
}
