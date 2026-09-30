package com.aieyaan.splynt.clover;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface CloverOAuthAttemptRepository extends JpaRepository<CloverOAuthAttempt, String> {
    @Modifying
    @Query("delete from CloverOAuthAttempt a where a.expiresAt < :now")
    int deleteExpired(@Param("now") java.time.OffsetDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CloverOAuthAttempt a where a.stateHash = :hash")
    Optional<CloverOAuthAttempt> findForConsumption(@Param("hash") String hash);
}
