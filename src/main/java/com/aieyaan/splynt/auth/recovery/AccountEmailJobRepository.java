package com.aieyaan.splynt.auth.recovery;

import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface AccountEmailJobRepository extends JpaRepository<AccountEmailJob, Long> {
    @Modifying
    @Query("delete from AccountEmailJob j where j.createdAt < :before")
    int deleteOld(OffsetDateTime before);
    @Modifying
    @Query("update AccountEmailJob j set j.state = 'CANCELLED' where j.state = 'PENDING' and j.createdAt < :before")
    int cancelExpired(OffsetDateTime before);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AccountEmailJob j where j.id = :id")
    Optional<AccountEmailJob> findLocked(Long id);
    Optional<AccountEmailJob> findFirstByUserIdAndPurposeAndStateOrderByIdDesc(Long userId, AccountActionToken.Purpose purpose, String state);
    List<AccountEmailJob> findTop20ByStateAndAvailableAtLessThanEqualOrderById(String state, OffsetDateTime now);
}
