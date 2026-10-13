package com.aieyaan.splynt.platform;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.aieyaan.splynt.tenant.*;
import com.aieyaan.splynt.security.*;
@SpringBootTest(properties="splynt.platform.mfa-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=") @Transactional
class PlatformSecurityTest {
 @Autowired JdbcTemplate db;@Autowired AppUserRepository users;@Autowired PasswordEncoder encoder;@Autowired WebApplicationContext context;@Autowired AuthenticationLimits limits;
 MockMvc mvc;String token;long id;
 final tools.jackson.databind.json.JsonMapper json=new tools.jackson.databind.json.JsonMapper();
 @BeforeEach void setup()throws Exception{
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  var u=new AppUser("secure-"+UUID.randomUUID()+"@example.com",encoder.encode("Private-test-password-42"),"Platform","Owner");u.markEmailVerified();u=users.saveAndFlush(u);id=u.getId();db.update("insert into platform_admin(singleton,user_id) values (1,?)",id);
  token=json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\""+u.getEmail()+"\",\"password\":\"Private-test-password-42\"}" )).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
 }
 @Test void requiresMfaRejectsReplayAndRecoveryReuseAndRecordsSafeAudit()throws Exception{
  mvc.perform(get("/api/platform/overview").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  String setup=mvc.perform(post("/api/platform/mfa/enroll").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
  String secret=json.readTree(setup).path("secret").asText();
  assertFalse(db.queryForObject("select secret_ciphertext from platform_mfa where user_id=?",String.class,id).contains(secret));
  String code=Totp.code(decode(secret),Instant.now().getEpochSecond()/30);
  String result=verify(code,200);var body=json.readTree(result);String elevated=body.path("accessToken").asText();
  assertEquals(8,body.path("recoveryCodes").size());
  mvc.perform(get("/api/platform/overview").header("Authorization","Bearer "+elevated)).andExpect(status().isOk());
  verify(code,401);
  String recovery=body.path("recoveryCodes").get(0).asText();verify(recovery,200);verify(recovery,401);
  mvc.perform(post("/api/platform/mfa/enroll").header("Authorization","Bearer "+token)).andExpect(status().isConflict());
  String audit=mvc.perform(get("/api/platform/audit").header("Authorization","Bearer "+elevated)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(audit.contains("ADMIN_MFA"));assertFalse(audit.contains(secret));assertFalse(audit.contains(recovery));assertFalse(audit.contains(token));
 }
 @Test void accountThrottleCannotBeBypassedByChangingForwardedIp()throws Exception{
  String email="missing-"+UUID.randomUUID()+"@example.com";
  for(int i=0;i<11;i++)mvc.perform(post("/api/auth/login").header("X-Forwarded-For","198.51.100."+i).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"incorrect-password\"}")).andExpect(status().is(i<10?401:429));
 }
 @Test void storeAccountCannotEnrollOrVerifyAdminMfa()throws Exception{
  var owner=users.saveAndFlush(new AppUser("store-"+UUID.randomUUID()+"@example.com","hash","Store","Owner"));
  var auth=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j->j.subject(owner.getId().toString()).issuedAt(Instant.now()));
  mvc.perform(post("/api/platform/mfa/enroll").with(auth)).andExpect(status().isForbidden());
  mvc.perform(post("/api/platform/mfa/verify").with(auth).contentType("application/json").content("{\"code\":\"123456\"}")).andExpect(status().isForbidden());
 }
 @Test void attemptsAreBoundedAndIndependentOfOuterRollback(){String key=UUID.randomUUID().toString();assertTrue(limits.consume("test",key,2,1));assertTrue(limits.consume("test",key,2,1));assertFalse(limits.consume("test",key,2,1));assertTrue(limits.consume("test",key+"other",2,1));}
 @Test void totpMatchesRfc6238Sha1Vectors(){byte[] key="12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);assertEquals("287082",Totp.code(key,59/30));assertEquals("081804",Totp.code(key,1111111109L/30));assertEquals("050471",Totp.code(key,1111111111L/30));}
 String verify(String code,int status)throws Exception{return mvc.perform(post("/api/platform/mfa/verify").header("Authorization","Bearer "+token).contentType("application/json").content("{\"code\":\""+code+"\"}")).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();}
 static byte[] decode(String value){var out=new java.io.ByteArrayOutputStream();int bits=0,n=0;for(char c:value.toCharArray()){n=(n<<5)|"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);bits+=5;if(bits>=8){bits-=8;out.write((n>>bits)&255);}}return out.toByteArray();}
}
