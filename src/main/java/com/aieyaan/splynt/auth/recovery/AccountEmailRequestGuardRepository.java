package com.aieyaan.splynt.auth.recovery;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface AccountEmailRequestGuardRepository extends JpaRepository<AccountEmailRequestGuard, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AccountEmailRequestGuard g where g.id = 1")
    Optional<AccountEmailRequestGuard> lock();
}
