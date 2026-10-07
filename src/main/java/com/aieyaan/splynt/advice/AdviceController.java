package com.aieyaan.splynt.advice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/stores/{storeId}/advice")
public class AdviceController {
    private final AdviceService service;
    public AdviceController(AdviceService service) { this.service = service; }
    @GetMapping @PreAuthorize("@storeAuthorizationService.canAccess(authentication, #storeId)")
    public AdviceService.View read(@PathVariable Long storeId) { return service.read(storeId); }
    @PostMapping @PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
    public AdviceService.View generate(@PathVariable Long storeId) { return service.generate(storeId); }
}
