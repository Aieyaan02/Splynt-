package com.aieyaan.splynt.reorder;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ReorderDraftRepository extends JpaRepository<ReorderDraft, UUID> {
    List<ReorderDraft> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ReorderDraft d where d.storeId = :storeId and d.id = :id")
    Optional<ReorderDraft> findLocked(@Param("storeId") Long storeId, @Param("id") UUID id);
}
