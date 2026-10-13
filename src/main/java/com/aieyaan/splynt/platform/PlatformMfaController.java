package com.aieyaan.splynt.platform;
import java.util.Map;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import com.aieyaan.splynt.security.AuthenticationLimits;
@RestController @RequestMapping("/api/platform/mfa") @PreAuthorize("@platformAccess.identity(authentication)")
public class PlatformMfaController {
 private final PlatformMfa mfa;private final AuthenticationLimits limits;
 public PlatformMfaController(PlatformMfa mfa,AuthenticationLimits limits){this.mfa=mfa;this.limits=limits;}
 @GetMapping public ResponseEntity<Map<String,Boolean>> status(JwtAuthenticationToken auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("enabled",mfa.enabled(id(auth))));}
 @PostMapping("/enroll") public ResponseEntity<Map<String,String>> enroll(JwtAuthenticationToken auth){fresh(auth);limits.check("mfa-enroll",auth.getName(),3,15);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mfa.enroll(id(auth),com.aieyaan.splynt.security.AccountTokenValidator.credentialVersion(auth.getToken())));}
 public record Code(@NotBlank @Size(max=32) String code){}
 @PostMapping("/verify") public ResponseEntity<PlatformMfa.Result> verify(JwtAuthenticationToken auth,@Valid @RequestBody Code request){fresh(auth);limits.check("mfa-code",auth.getName(),5,5);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mfa.verify(id(auth),com.aieyaan.splynt.security.AccountTokenValidator.credentialVersion(auth.getToken()),request.code()));}
 private long id(JwtAuthenticationToken auth){return Long.parseLong(auth.getName());}
 private void fresh(JwtAuthenticationToken auth){var issued=auth.getToken().getIssuedAt();if(issued==null || issued.isBefore(Instant.now().minusSeconds(300)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in again before verifying");}
}
