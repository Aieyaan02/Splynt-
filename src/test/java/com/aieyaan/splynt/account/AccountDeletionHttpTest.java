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
class AccountDeletionHttpTest {
 @Autowired WebApplicationContext context;
 @Autowired AppUserRepository users;
 @Autowired OrganizationRepository orgs;
 @Autowired StoreRepository stores;
 @Autowired OrganizationMembershipRepository members;
 @Autowired PasswordEncoder encoder;
 @Autowired JwtTokenService tokens;
 @Autowired org.springframework.jdbc.core.JdbcTemplate db;
 MockMvc mvc; AppUser owner; Organization org; String token;
 @BeforeEach void setup() {
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  String id=UUID.randomUUID().toString();
  owner=users.saveAndFlush(new AppUser(id+"@example.com",encoder.encode("delete-test-password"),"Test","Owner"));
  org=orgs.saveAndFlush(new Organization("Shop",id));
  stores.saveAndFlush(new Store(org,"Shop",id));
  members.saveAndFlush(new OrganizationMembership(org,owner,MembershipRole.OWNER));
  token=tokens.generateAccessToken(owner).value();
 }
 String body(String password,String confirm) {return "{\"password\":\""+password+"\",\"confirmation\":\""+confirm+"\"}";}
 @Test void deletionRequiresOwnerPasswordAndExactConfirmationAndPreservesOtherWorkspace() throws Exception {
  var other=orgs.saveAndFlush(new Organization("Other",UUID.randomUUID().toString()));
  Long storeId=db.queryForObject("SELECT id FROM stores WHERE organization_id=?",Long.class,org.getId());
  var otherStore=stores.saveAndFlush(new Store(other,"Other",UUID.randomUUID().toString()));
  db.update("INSERT INTO products (store_id,barcode,name) VALUES (?,?,?)",storeId,"delete-product","Delete me");
  db.update("INSERT INTO products (store_id,barcode,name) VALUES (?,?,?)",otherStore.getId(),"keep-product","Keep me");
  Long productId=db.queryForObject("SELECT id FROM products WHERE store_id=?",Long.class,storeId);
  db.update("INSERT INTO inventory_movements (product_id,movement_type,quantity_change,quantity_before,quantity_after) VALUES (?,'RESTOCK',1,0,1)",productId);
  db.update("INSERT INTO sales_events (store_id,product_id,order_id,line_id,units,occurred_at) VALUES (?,?,'order','line',1,CURRENT_TIMESTAMP)",storeId,productId);
  db.update("INSERT INTO store_ai_reports (store_id,attempted_at) VALUES (?,CURRENT_TIMESTAMP)",storeId);
  db.update("INSERT INTO clover_oauth_credentials (merchant_id,store_id,access_token,refresh_token,created_at,updated_at) VALUES (?,?,'test','test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),storeId);
  db.update("INSERT INTO clover_oauth_attempts (state_hash,browser_hash,store_id,user_id,expires_at) VALUES (?,?, ?,?,CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),UUID.randomUUID().toString(),storeId,owner.getId());
  String path="/api/me/organizations/"+org.getId();
  mvc.perform(delete(path).contentType("application/json").content(body("delete-test-password",org.getSlug()))).andExpect(status().isUnauthorized());
  mvc.perform(delete("/api/me/organizations/"+other.getId()).header("Authorization","Bearer "+token).contentType("application/json").content(body("delete-test-password",other.getSlug()))).andExpect(status().isForbidden());
  mvc.perform(delete(path).header("Authorization","Bearer "+token).contentType("application/json").content(body("wrong",org.getSlug()))).andExpect(status().isForbidden());
  mvc.perform(delete(path).header("Authorization","Bearer "+token).contentType("application/json").content(body("delete-test-password","wrong"))).andExpect(status().isBadRequest());
  assertTrue(orgs.existsById(org.getId()));
  mvc.perform(delete(path).header("Authorization","Bearer "+token).contentType("application/json").content(body("delete-test-password",org.getSlug()))).andExpect(status().isOk());
  assertFalse(orgs.existsById(org.getId())); assertTrue(orgs.existsById(other.getId())); assertTrue(users.existsById(owner.getId()));
  assertEquals(0L,db.queryForObject("SELECT count(*) FROM products WHERE store_id=?",Long.class,storeId));
  assertEquals(1L,db.queryForObject("SELECT count(*) FROM products WHERE store_id=?",Long.class,otherStore.getId()));
  assertEquals(0L,db.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=?",Long.class,productId));
  assertEquals(0L,db.queryForObject("SELECT count(*) FROM clover_oauth_credentials WHERE store_id=?",Long.class,storeId));
  mvc.perform(delete("/api/me").header("Authorization","Bearer "+token).contentType("application/json").content(body("delete-test-password",owner.getEmail()))).andExpect(status().isOk());
  assertFalse(users.existsById(owner.getId()));
  mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void cannotDeleteOwnerAccountBeforeWorkspace() throws Exception {
  mvc.perform(delete("/api/me").header("Authorization","Bearer "+token).contentType("application/json").content(body("delete-test-password",owner.getEmail()))).andExpect(status().isBadRequest());
  assertTrue(users.existsById(owner.getId())); assertTrue(orgs.existsById(org.getId()));
 }
}
