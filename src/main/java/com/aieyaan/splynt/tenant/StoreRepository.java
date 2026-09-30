package com.aieyaan.splynt.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository
        extends JpaRepository<Store, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Store s where s.id = :id")
    Optional<Store> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

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