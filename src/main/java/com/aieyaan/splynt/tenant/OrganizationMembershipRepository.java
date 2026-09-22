package com.aieyaan.splynt.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, Long> {

    Optional<OrganizationMembership>
            findByOrganizationIdAndUserIdAndActiveTrue(
                    Long organizationId,
                    Long userId
            );

    List<OrganizationMembership>
            findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(
                    Long userId
            );

    boolean existsByOrganizationIdAndUserId(
            Long organizationId,
            Long userId
    );
}