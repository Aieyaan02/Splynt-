package com.aieyaan.splynt.advice;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name = "store_ai_reports")
public class AdviceReport {
    @Id Long storeId;
    @Column(nullable = false) OffsetDateTime attemptedAt;
    OffsetDateTime generatedAt;
    @Column(length = 120) String model;
    @Column(columnDefinition = "text") String reportJson;
    @Column(columnDefinition = "text") String evidenceJson;
    @Column(length = 255) String errorMessage;
    protected AdviceReport() {}
    AdviceReport(Long storeId) { this.storeId = storeId; }
}
