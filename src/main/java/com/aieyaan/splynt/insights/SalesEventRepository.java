package com.aieyaan.splynt.insights;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SalesEventRepository extends JpaRepository<SalesEvent, Long> {
    void deleteByStoreIdAndOrderId(Long storeId, String orderId);
    List<SalesEvent> findAllByStoreIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(Long storeId, OffsetDateTime since);
}
