package com.aieyaan.splynt.reorder;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "reorder_drafts")
public class ReorderDraft {
    @Id UUID id;
    @Column(nullable = false) Long storeId;
    @Version long version;
    @Column(nullable = false, length = 150) String supplier;
    @Column(nullable = false, length = 255) String supplierContact;
    @Column(nullable = false, length = 1000) String notes;
    @Column(nullable = false, columnDefinition = "text") String itemsJson;
    @Column(nullable = false, length = 16) String status;
    @Column(nullable = false) OffsetDateTime createdAt;
    @Column(nullable = false) OffsetDateTime updatedAt;
    OffsetDateTime orderedAt;
    protected ReorderDraft() {}
}
