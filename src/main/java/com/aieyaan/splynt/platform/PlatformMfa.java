package com.aieyaan.splynt.platform;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.aieyaan.splynt.clover.CloverTokenCipher;
import com.aieyaan.splynt.tenant.AppUserRepository;
import com.aieyaan.splynt.security.JwtTokenService;
@Service
public class PlatformMfa {
 private final JdbcTemplate db;private final CloverTokenCipher cipher;private final AppUserRepository users;private final JwtTokenService tokens;
 public PlatformMfa(JdbcTemplate db,AppUserRepository users,JwtTokenService tokens,@Value("${splynt.platform.mfa-key:}") String key){this.db=db;this.users=users;this.tokens=tokens;cipher=new CloverTokenCipher(key);}
 public boolean enabled(long id){return db.queryForObject("select count(*) from platform_mfa where user_id=? and enabled=true",Integer.class,id)>0;}
 @Transactional public Map<String,String> enroll(long id,long credentialVersion){
  guard(id,credentialVersion);
  if(enabled(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Authenticator already enrolled");
  byte[] secret=new byte[20];new SecureRandom().nextBytes(secret);
  String encrypted=cipher.encrypt(id+":"+Base64.getEncoder().encodeToString(secret));
  db.update("delete from platform_mfa where user_id=?",id);
  db.update("insert into platform_mfa(user_id,secret_ciphertext,enabled,last_step,created_at) values (?,?,false,-1,?)",id,encrypted,OffsetDateTime.now());
  return Map.of("secret",Totp.base32(secret));
 }
 public record Result(String accessToken,List<String> recoveryCodes){}
 @Transactional public Result verify(long id,long credentialVersion,String code){
  guard(id,credentialVersion);
  var rows=db.queryForList("select secret_ciphertext,enabled,last_step,created_at from platform_mfa where user_id=? for update",id);
  if(rows.isEmpty())throw invalid();var row=rows.getFirst();boolean enrolled=(Boolean)row.get("enabled");
  if(!enrolled && ((OffsetDateTime)row.get("created_at")).isBefore(OffsetDateTime.now().minusMinutes(10)))throw invalid();
  boolean valid=false;long step=Instant.now().getEpochSecond()/30;
  if(code.matches("[0-9]{6}")){
   String decoded=cipher.decrypt((String)row.get("secret_ciphertext"));String prefix=id+":";if(!decoded.startsWith(prefix))throw invalid();
   byte[] secret=Base64.getDecoder().decode(decoded.substring(prefix.length()));long last=((Number)row.get("last_step")).longValue();
   for(long s=step-1;s<=step+1;s++)if(s>last && MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),Totp.code(secret,s).getBytes(StandardCharsets.US_ASCII))){db.update("update platform_mfa set last_step=? where user_id=?",s,id);valid=true;break;}
  }else if(enrolled && code.matches("[0-9a-f]{32}")){valid=db.update("delete from platform_recovery_code where user_id=? and code_hash=?",id,hash(code))==1;}
  if(!valid)throw invalid();
  List<String> recovery=new ArrayList<>();
  if(!enrolled){db.update("update platform_mfa set enabled=true where user_id=?",id);for(int i=0;i<8;i++){byte[] b=new byte[16];new SecureRandom().nextBytes(b);String value=HexFormat.of().formatHex(b);recovery.add(value);db.update("insert into platform_recovery_code(user_id,code_hash) values (?,?)",id,hash(value));}}
  var user=users.findById(id).orElseThrow();return new Result(tokens.generatePlatformToken(user).value(),List.copyOf(recovery));
 }
 private void guard(long id,long credentialVersion){
  var row=db.queryForMap("select enabled,email_verified,credential_version from app_users where id=? for update",id);
  if(!Boolean.TRUE.equals(row.get("enabled")) || !Boolean.TRUE.equals(row.get("email_verified")) || ((Number)row.get("credential_version")).longValue()!=credentialVersion || db.queryForObject("select count(*) from platform_admin where user_id=?",Integer.class,id)!=1)throw invalid();
 }
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid or expired code");}
}
