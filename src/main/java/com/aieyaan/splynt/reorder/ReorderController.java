package com.aieyaan.splynt.reorder;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/stores/{storeId}/reorder-drafts")
public class ReorderController {
    private final ReorderService service;
    public ReorderController(ReorderService service) { this.service = service; }
    @GetMapping @PreAuthorize("@storeAuthorizationService.canAccess(authentication, #storeId)")
    public List<ReorderService.View> list(@PathVariable Long storeId) { return service.list(storeId); }
    @PostMapping @PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
    public ReorderService.View create(@PathVariable Long storeId, @Valid @RequestBody ReorderService.Input input) { return service.create(storeId, input); }
    @PutMapping("/{id}") @PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
    public ReorderService.View update(@PathVariable Long storeId, @PathVariable UUID id, @Valid @RequestBody ReorderService.Input input) { return service.update(storeId, id, input); }
    @PostMapping("/{id}/ordered") @PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
    public ReorderService.View ordered(@PathVariable Long storeId, @PathVariable UUID id, @Valid @RequestBody ReorderService.Version input) { return service.ordered(storeId, id, input.version()); }
}
