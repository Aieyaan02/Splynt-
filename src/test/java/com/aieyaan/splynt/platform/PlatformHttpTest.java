package com.aieyaan.splynt.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.util.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.clover.*;
import com.aieyaan.splynt.clover.dto.CloverSyncResponse;

@SpringBootTest @Transactional
class PlatformHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired AppUserRepository users;
    @Autowired OrganizationMembershipRepository memberships;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired PlatformService service;
    MockMvc mvc; AppUser admin, owner; Store store;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix=UUID.randomUUID().toString();
        var org=organizations.saveAndFlush(new Organization("Monitoring shop",suffix));
        store=stores.saveAndFlush(new Store(org,"Monitoring store",suffix));
        owner=users.saveAndFlush(new AppUser("owner-"+suffix+"@example.com","private-password-hash","Shop","Owner"));
        memberships.saveAndFlush(new OrganizationMembership(org,owner,MembershipRole.OWNER));
        admin=new AppUser("admin-"+suffix+"@example.com","admin-private-hash","Platform","Owner");admin.markEmailVerified();admin=users.saveAndFlush(admin);
        db.update("insert into platform_admin(singleton,user_id) values (1,?)",admin.getId());
    }
    @Test void dedicatedAdminCanReadAllViewsButStoreOwnerAndAnonymousCannot() throws Exception {
        for(String endpoint:List.of("overview","stores","users","organizations")) {
            mvc.perform(get("/api/platform/"+endpoint)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/platform/"+endpoint).with(jwt().jwt(j->j.subject(owner.getId().toString())))).andExpect(status().isForbidden());
            mvc.perform(get("/api/platform/"+endpoint).with(jwt().jwt(j->j.subject(admin.getId().toString())))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        }
        mvc.perform(get("/api/platform/access").with(jwt().jwt(j->j.subject(owner.getId().toString())))).andExpect(jsonPath("$.allowed").value(false));
    }
    @Test void disabledUnverifiedStaleAndRevokedAdminsAreDenied() throws Exception {
        var token=jwt().jwt(j->j.subject(admin.getId().toString()));
        admin.disable();users.saveAndFlush(admin);
        mvc.perform(get("/api/platform/overview").with(token)).andExpect(status().isForbidden());
        admin.enable();admin.changeEmail("changed-"+UUID.randomUUID()+"@example.com");users.saveAndFlush(admin);
        mvc.perform(get("/api/platform/overview").with(jwt().jwt(j->j.subject(admin.getId().toString()).claim("credential_version",admin.getCredentialVersion())))).andExpect(status().isForbidden());
        admin.markEmailVerified();users.saveAndFlush(admin);
        mvc.perform(get("/api/platform/overview").with(token)).andExpect(status().isForbidden());
        db.update("delete from platform_admin");
        mvc.perform(get("/api/platform/overview").with(jwt().jwt(j->j.subject(admin.getId().toString()).claim("credential_version",admin.getCredentialVersion())))).andExpect(status().isForbidden());
    }
    @Test void syncMetricsRetainFailureHistoryWithoutLeakingProviderErrorsOrTokens() throws Exception {
        var connection=new CloverOAuthCredential("merchant-"+UUID.randomUUID(),"PRIVATE_ACCESS_TOKEN","PRIVATE_REFRESH_TOKEN",null,null);
        connection.assignStore(store.getId());connection.markSyncFailed("PRIVATE_PROVIDER_RESPONSE_WITH_CREDENTIAL");
        connection.recordInventoryReview(new CloverSyncResponse("merchant",12,5,6,1,OffsetDateTime.now().plusSeconds(1),0,List.of()));
        credentials.saveAndFlush(connection);
        String body=mvc.perform(get("/api/platform/stores").with(jwt().jwt(j->j.subject(admin.getId().toString())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("PRIVATE_"));assertFalse(body.contains("merchant-"));assertFalse(body.contains("accessToken"));
        var row=service.stores(0,false).items().stream().filter(s->s.id()==store.getId()).findFirst().orElseThrow();
        assertNotNull(row.lastFailedSync());assertNotNull(row.lastSuccessfulSync());assertEquals(11,row.lastImportedProducts());assertEquals(12,row.lastReceivedProducts());assertEquals("CONNECTED",row.connectionStatus());assertTrue(row.attention().isEmpty());
    }
    @Test void attentionFlagsMissingAndStaleSyncsButExcludesInactiveStores() {
        assertTrue(service.stores(0,true).items().stream().anyMatch(s->s.id()==store.getId()));
        var c=new CloverOAuthCredential("merchant-"+UUID.randomUUID(),"token","refresh",null,null);c.assignStore(store.getId());
        c.recordInventoryReview(new CloverSyncResponse("merchant",0,0,0,0,OffsetDateTime.now().minusDays(2),0,List.of()));credentials.saveAndFlush(c);
        assertTrue(service.stores(0,true).items().stream().filter(s->s.id()==store.getId()).findFirst().orElseThrow().attention().contains("Inventory older than 24 hours"));
        db.update("update stores set active=false where id=?",store.getId());
        assertFalse(service.stores(0,true).items().stream().anyMatch(s->s.id()==store.getId()));
    }
    @Test void activityIsRecordedAndThrottledAndUserDtoDoesNotExposePasswords() throws Exception {
        var token=jwt().jwt(j->j.subject(admin.getId().toString()));
        mvc.perform(get("/api/platform/overview").with(token)).andExpect(status().isOk());
        var first=db.queryForObject("select last_activity_at from app_users where id=?",OffsetDateTime.class,admin.getId());assertNotNull(first);
        String result=mvc.perform(get("/api/platform/users").with(token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("private-hash"));assertFalse(result.contains("passwordHash"));
        assertEquals(first,db.queryForObject("select last_activity_at from app_users where id=?",OffsetDateTime.class,admin.getId()));
    }
    @Test void totalsAndPaginationAreBounded() throws Exception {
        assertEquals(db.queryForObject("select count(*) from organizations",Long.class),service.overview().organizations());
        assertEquals(db.queryForObject("select count(*) from app_users",Long.class),service.overview().users());
        for(int i=0;i<27;i++) users.save(new AppUser(UUID.randomUUID()+"@example.com","hash","Test","User"));users.flush();
        assertEquals(25,service.users(0).items().size());assertTrue(service.users(1).items().size()>0);
        mvc.perform(get("/api/platform/users?page=-1").with(jwt().jwt(j->j.subject(admin.getId().toString())))).andExpect(status().isBadRequest());
    }
}
