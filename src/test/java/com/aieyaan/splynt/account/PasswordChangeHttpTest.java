package com.aieyaan.splynt.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.security.JwtTokenService;

@SpringBootTest
class PasswordChangeHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService tokens;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
    @org.springframework.beans.factory.annotation.Value("${splynt.security.jwt.issuer}") String issuer;
    MockMvc mvc;
    AppUser user;
    String token;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", encoder.encode("old-password-123"), "Test", "User"));
        token = tokens.generateAccessToken(user).value();
    }
    String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }
    @Test void changedPasswordInvalidatesAllOldTokensAndNewLoginWorks() throws Exception {
        String otherSession = tokens.generateAccessToken(user).value();
        mvc.perform(post("/api/me/password").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body("old-password-123", "new-password-456")))
                .andExpect(status().isNoContent());
        for (String expired : new String[]{token, otherSession}) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"old-password-123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"new-password-456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        var changed = users.findById(user.getId()).orElseThrow();
        assertEquals(1, changed.getCredentialVersion());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + tokens.generateAccessToken(changed).value()))
                .andExpect(status().isOk());
    }
    @Test void wrongCurrentPasswordAndInvalidNewPasswordLeaveSessionUsable() throws Exception {
        mvc.perform(post("/api/me/password").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body("incorrect", "new-password-456")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/password").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body("old-password-123", "🔑".repeat(19))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.newPassword").exists());
        assertEquals(0, users.findById(user.getId()).orElseThrow().getCredentialVersion());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }
    @Test void legacyTokenWorksUntilCredentialsChangeAndOtherUsersStaySignedIn() throws Exception {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(issuer).subject(user.getId().toString()).issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(300)).id(UUID.randomUUID().toString()).build();
        String legacy = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims)).getTokenValue();
        var other = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@example.com", encoder.encode("other-password"), "Other", "User"));
        String otherToken = tokens.generateAccessToken(other).value();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + legacy)).andExpect(status().isOk());
        mvc.perform(post("/api/me/password").header("Authorization", "Bearer " + legacy)
                .contentType("application/json").content(body("old-password-123", "new-password-456")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + legacy)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + otherToken)).andExpect(status().isOk());
    }

    @Test void anonymousRequestsCannotChangePassword() throws Exception {
        mvc.perform(post("/api/me/password").contentType("application/json")
                .content(body("old-password-123", "new-password-456"))).andExpect(status().isUnauthorized());
    }
}
