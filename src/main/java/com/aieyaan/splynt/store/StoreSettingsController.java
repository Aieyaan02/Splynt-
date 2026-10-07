package com.aieyaan.splynt.store;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class StoreSettingsController {
    private final StoreSettingsService service;
    public StoreSettingsController(StoreSettingsService service) { this.service = service; }
    @GetMapping("/stores/{storeId}/settings")
    @PreAuthorize("@storeAuthorizationService.canAccess(authentication, #storeId)")
    public StoreSettingsResponse read(@PathVariable Long storeId) { return service.read(storeId); }
    @PatchMapping("/stores/{storeId}/settings")
    @PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
    public StoreSettingsResponse update(@PathVariable Long storeId, @Valid @RequestBody StoreSettingsRequest request) {
        return service.update(storeId, request);
    }
    @PostMapping("/organizations/{organizationId}/stores")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreSettingsResponse create(@PathVariable Long organizationId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StoreSettingsRequest request) {
        return service.create(organizationId, Long.valueOf(jwt.getSubject()), request);
    }
}
