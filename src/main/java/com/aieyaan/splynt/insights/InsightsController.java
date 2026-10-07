package com.aieyaan.splynt.insights;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/stores/{storeId}/insights")
@PreAuthorize("@storeAuthorizationService.canAccess(authentication, #storeId)")
public class InsightsController {
    private final InsightsService insights;
    public InsightsController(InsightsService insights) { this.insights = insights; }
    @GetMapping public InsightsService.Insights read(@PathVariable Long storeId) { return insights.read(storeId); }
}
