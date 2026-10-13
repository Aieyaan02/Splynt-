package com.aieyaan.splynt.platform;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController @RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformAccess access;
    private final PlatformService service;
    public PlatformController(PlatformAccess access,PlatformService service) {this.access=access;this.service=service;}
    @GetMapping("/access") public Map<String,Boolean> access(Authentication authentication) { return Map.of("allowed",access.allowed(authentication)); }
    @GetMapping("/audit") @PreAuthorize("@platformAccess.allowed(authentication)")
    public ResponseEntity<PlatformService.Page<PlatformService.AuditRow>> audit(@RequestParam(defaultValue="0") int page) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.audit(page));}
    @GetMapping("/overview") @PreAuthorize("@platformAccess.allowed(authentication)")
    public ResponseEntity<PlatformService.Overview> overview() {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.overview());}
    @GetMapping("/stores") @PreAuthorize("@platformAccess.allowed(authentication)")
    public ResponseEntity<PlatformService.Page<PlatformService.StoreRow>> stores(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="false") boolean attentionOnly) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.stores(page,attentionOnly));}
    @GetMapping("/users") @PreAuthorize("@platformAccess.allowed(authentication)")
    public ResponseEntity<PlatformService.Page<PlatformService.UserRow>> users(@RequestParam(defaultValue="0") int page) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.users(page));}
    @GetMapping("/organizations") @PreAuthorize("@platformAccess.allowed(authentication)")
    public ResponseEntity<PlatformService.Page<PlatformService.OrganizationRow>> organizations(@RequestParam(defaultValue="0") int page) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.organizations(page));}
}
