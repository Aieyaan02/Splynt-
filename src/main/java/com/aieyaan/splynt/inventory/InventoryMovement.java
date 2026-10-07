package com.aieyaan.splynt.inventory;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Objects;

import com.aieyaan.splynt.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private InventoryMovementType movementType;

    @Column(name = "quantity_change", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityChange;

    @Column(name = "quantity_before", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantityAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryMovementSource source;

    @Column(length = 255)
    private String note;

    @Column(name = "external_reference", length = 128)
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(
            Product product,
            InventoryMovementType movementType,
            BigDecimal quantityChange,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            InventoryMovementSource source,
            String note,
            String externalReference) {

        this.product = Objects.requireNonNull(
                product,
                "Product is required");

        this.movementType = Objects.requireNonNull(
                movementType,
                "Movement type is required");

        this.source = Objects.requireNonNull(
                source,
                "Movement source is required");

        validateQuantities(
                movementType,
                quantityChange,
                quantityBefore,
                quantityAfter);

        this.quantityChange = quantityChange.setScale(6, java.math.RoundingMode.UNNECESSARY);
        if (this.quantityChange.precision() > 19) throw new IllegalArgumentException("Inventory change is too large");
        this.quantityBefore = Product.stockAmount(quantityBefore);
        this.quantityAfter = Product.stockAmount(quantityAfter);
        this.note = normalizeText(note);
        this.externalReference = normalizeText(externalReference);
    }

    public InventoryMovement(Product product, InventoryMovementType type, int change, int before, int after, InventoryMovementSource source, String note, String reference) {
        this(product, type, BigDecimal.valueOf(change), BigDecimal.valueOf(before), BigDecimal.valueOf(after), source, note, reference);
    }

    private void validateQuantities(
            InventoryMovementType movementType,
            BigDecimal quantityChange,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter) {

        if (quantityChange.signum() == 0) {
            throw new IllegalArgumentException(
                    "Inventory quantity change cannot be zero");
        }

        if ((quantityBefore.signum() < 0 || quantityAfter.signum() < 0) && !(source == InventoryMovementSource.CLOVER && movementType == InventoryMovementType.ADJUSTMENT)) {
            throw new IllegalArgumentException(
                    "Inventory quantities cannot be negative");
        }

        if (quantityAfter.compareTo(quantityBefore.add(quantityChange)) != 0) {
            throw new IllegalArgumentException(
                    "Quantity after must equal quantity before plus the change");
        }

        if (movementType == InventoryMovementType.SALE
                && quantityChange.signum() >= 0) {

            throw new IllegalArgumentException(
                    "A sale must decrease inventory");
        }

        if (movementType == InventoryMovementType.RESTOCK
                && quantityChange.signum() <= 0) {

            throw new IllegalArgumentException(
                    "A restock must increase inventory");
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public InventoryMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public InventoryMovementSource getSource() {
        return source;
    }

    public String getNote() {
        return note;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}