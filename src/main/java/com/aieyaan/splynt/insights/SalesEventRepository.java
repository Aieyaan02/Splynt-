package com.aieyaan.splynt.insights;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SalesEventRepository extends JpaRepository<SalesEvent, Long> {
    @org.springframework.data.jpa.repository.Query("select e from SalesEvent e where e.storeId = :storeId and e.occurredAt >= :start and e.occurredAt < :end order by e.occurredAt")
    List<SalesEvent> findWindow(@org.springframework.data.repository.query.Param("storeId") Long storeId,
            @org.springframework.data.repository.query.Param("start") OffsetDateTime start,
            @org.springframework.data.repository.query.Param("end") OffsetDateTime end);
    void deleteByStoreIdAndOrderId(Long storeId, String orderId);
    List<SalesEvent> findAllByStoreIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(Long storeId, OffsetDateTime since);
}
