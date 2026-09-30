package com.aieyaan.splynt.clover;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieyaan.splynt.clover.dto.CloverSyncResponse;

@RestController
@RequestMapping(
        "/api/stores/{storeId}/integrations/clover"
)
@PreAuthorize(
        "@storeAuthorizationService.canManage("
                + "authentication, #storeId)"
)
public class CloverInventoryController {

    private final CloverSyncJobs syncService;

    public CloverInventoryController(
            CloverSyncJobs syncService) {

        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public CloverSyncResponse synchronize(
            @PathVariable Long storeId) {

        return syncService.run(storeId);
    }
}