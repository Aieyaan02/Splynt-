package com.aieyaan.splynt.auth.recovery;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.*;
public interface AccountEmailRequestRepository extends JpaRepository<AccountEmailRequest, Long> {
    long countByCreatedAtAfter(OffsetDateTime since);
    long countByEmailHashAndCreatedAtAfter(String hash, OffsetDateTime since);
    @Modifying @Query("delete from AccountEmailRequest r where r.createdAt < :before")
    void deleteExpired(OffsetDateTime before);
}
