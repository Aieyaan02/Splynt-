package com.aieyaan.splynt.insights;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
@Entity @Table(name = "sales_events", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "order_id", "line_id"}))
public class SalesEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "order_id", nullable = false, length = 64) private String orderId;
    @Column(name = "line_id", nullable = false, length = 64) private String lineId;
    @Column(nullable = false, precision = 18, scale = 3) private BigDecimal units;
    @Column(name = "quantity_unit", length = 64) private String quantityUnit;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    protected SalesEvent() {}
    public SalesEvent(Long storeId, Long productId, String orderId, String lineId, BigDecimal units, OffsetDateTime occurredAt) {
        this(storeId, productId, orderId, lineId, units, occurredAt, "items");
    }
    public SalesEvent(Long storeId, Long productId, String orderId, String lineId, BigDecimal units, OffsetDateTime occurredAt, String quantityUnit) {
        if (quantityUnit != null && (quantityUnit.isBlank() || quantityUnit.length() > 64))
            throw new IllegalArgumentException("Sales quantity unit must be at most 64 characters");
        this.quantityUnit = quantityUnit;
        this.storeId = storeId; this.productId = productId; this.orderId = orderId; this.lineId = lineId;
        this.units = units; this.occurredAt = occurredAt;
    }
    public String getQuantityUnit() { return quantityUnit; }
    public Long getProductId() { return productId; }
    public BigDecimal getUnits() { return units; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getOrderId() { return orderId; }
}
