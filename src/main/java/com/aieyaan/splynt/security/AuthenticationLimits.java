package com.aieyaan.splynt.security;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class AuthenticationLimits {
 private final JdbcTemplate db;
 public AuthenticationLimits(JdbcTemplate db){this.db=db;}
 // Separate committed transaction: failed credentials cannot roll back the attempt count.
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public boolean consume(String category,String identity,int maximum,int minutes){
  db.queryForObject("select id from authentication_guard where id=1 for update",Integer.class);
  var now=OffsetDateTime.now();db.update("delete from authentication_limit where expires_at <= ?",now);
  String key;
  try{key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((category+":"+identity).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  var counts=db.queryForList("select attempts from authentication_limit where bucket=?",Integer.class,key);
  if(counts.isEmpty()){
   if(db.queryForObject("select count(*) from authentication_limit",Integer.class)>=10000)return false;
   db.update("insert into authentication_limit(bucket,attempts,expires_at) values (?,1,?)",key,now.plusMinutes(minutes));return true;
  }
  if(counts.getFirst()>=maximum)return false;
  db.update("update authentication_limit set attempts=attempts+1 where bucket=?",key);return true;
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void check(String category,String identity,int maximum,int minutes){if(!consume(category,identity,maximum,minutes))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts. Try again later.");}
}
