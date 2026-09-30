package com.aieyaan.splynt.contact;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
    long countByCreatedAtAfter(OffsetDateTime since);
}
