package com.aieyaan.splynt.contact;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from ContactInquiry i where i.id = :id")
    java.util.Optional<ContactInquiry> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.List<ContactInquiry> findTop10ByNotificationStateAndNotificationAvailableAtLessThanEqualOrderById(String state, OffsetDateTime now);
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
    long countByCreatedAtAfter(OffsetDateTime since);
}
