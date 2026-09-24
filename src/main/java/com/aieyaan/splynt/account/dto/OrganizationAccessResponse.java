package com.aieyaan.splynt.account.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.aieyaan.splynt.tenant.MembershipRole;
import com.aieyaan.splynt.tenant.SubscriptionPlan;
import com.aieyaan.splynt.tenant.SubscriptionStatus;

public record OrganizationAccessResponse(
        Long id,
        String name,
        String slug,
        MembershipRole role,
        SubscriptionPlan subscriptionPlan,
        SubscriptionStatus subscriptionStatus,
        OffsetDateTime trialEndsAt,
        List<StoreSummaryResponse> stores) {
}