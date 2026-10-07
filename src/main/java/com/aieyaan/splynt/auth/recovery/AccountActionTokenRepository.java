package com.aieyaan.splynt.auth.recovery;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AccountActionToken t where t.tokenHash = :hash")
    Optional<AccountActionToken> findLocked(String hash);
    @Modifying
    @Query("delete from AccountActionToken t where t.userId = :userId and t.purpose = :purpose")
    void removePrevious(Long userId, AccountActionToken.Purpose purpose);
}
