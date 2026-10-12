package com.aieyaan.splynt.insights;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "splynt.sales.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/stores/{storeId}/integrations/clover/history")
@PreAuthorize("@storeAuthorizationService.canManage(authentication, #storeId)")
public class CloverSalesBackfillController {
    private final CloverSalesBackfillService service;
    public CloverSalesBackfillController(CloverSalesBackfillService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@PathVariable Long storeId) { service.request(storeId); }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long storeId) { service.cancel(storeId); }
}
