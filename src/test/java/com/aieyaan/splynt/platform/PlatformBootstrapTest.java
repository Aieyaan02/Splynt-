package com.aieyaan.splynt.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest @Transactional
class PlatformBootstrapTest {
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired jakarta.validation.Validator validator;
    @Autowired PasswordEncoder encoder;
    @Autowired WebApplicationContext context;
    @Test void createsSeparateAccountWithoutStoreAndCanSignInToPlatformUsingRealJwt() throws Exception {
        String email="platform-"+UUID.randomUUID()+"@example.com";
        String password="Platform-only-test-42";
        var setup=new PlatformBootstrap(users,db,validator,email,encoder.encode(password));
        setup.run(new DefaultApplicationArguments(new String[0]));
        var user=users.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(user.isEmailVerified());
        assertEquals(0,db.queryForObject("select count(*) from organization_memberships where user_id=?",Integer.class,user.getId()));
        setup.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(1,db.queryForObject("select count(*) from platform_admin",Integer.class));
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token=new tools.jackson.databind.json.JsonMapper().readTree(result).path("accessToken").asText();
        mvc.perform(get("/api/platform/overview").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.organizations.length()").value(0));
    }
    @Test void existingAccountIsNeverPromotedByEmail() {
        String email="existing-"+UUID.randomUUID()+"@example.com";
        users.saveAndFlush(new AppUser(email,"hash","Existing","Owner"));
        var setup=new PlatformBootstrap(users,db,validator,email,encoder.encode("Platform-only-test-42"));
        assertThrows(IllegalStateException.class,()->setup.run(new DefaultApplicationArguments(new String[0])));
        assertEquals(0,db.queryForObject("select count(*) from platform_admin",Integer.class));
    }
    @Test void invalidProvisioningCannotCreateAdmin() {
        var setup=new PlatformBootstrap(users,db,validator,"not-an-email","not-a-hash");
        assertThrows(IllegalStateException.class,()->setup.run(new DefaultApplicationArguments(new String[0])));
        assertEquals(0,db.queryForObject("select count(*) from platform_admin",Integer.class));
    }
}
