package com.aieyaan.splynt.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository
        extends JpaRepository<Store, Long> {

    Optional<Store> findByIdAndOrganizationId(
            Long storeId,
            Long organizationId
    );

    Optional<Store> findByOrganizationIdAndSlug(
            Long organizationId,
            String slug
    );

    List<Store> findAllByOrganizationIdAndActiveTrueOrderByNameAsc(
            Long organizationId
    );

    boolean existsByOrganizationIdAndSlug(
            Long organizationId,
            String slug
    );
}