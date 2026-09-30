package com.aieyaan.splynt.clover;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface CloverOAuthCredentialRepository extends JpaRepository<CloverOAuthCredential, Long> {
    Optional<CloverOAuthCredential> findByMerchantId(String merchantId);
    Optional<CloverOAuthCredential> findByStoreId(Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CloverOAuthCredential c where c.storeId = :storeId")
    Optional<CloverOAuthCredential> findLockedByStoreId(@Param("storeId") Long storeId);
    List<CloverOAuthCredential> findAllByStoreIdIsNotNull();
}
