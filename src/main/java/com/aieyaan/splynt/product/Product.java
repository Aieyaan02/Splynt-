package com.aieyaan.splynt.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import com.aieyaan.splynt.tenant.Store;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_products_store_barcode",
                        columnNames = {"store_id", "barcode"}
                ),
                @UniqueConstraint(
                        name = "uq_products_store_clover_item",
                        columnNames = {
                                "store_id",
                                "clover_item_id"
                        }
                )
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "store_id",
            nullable = false
    )
    private Store store;

    @Column(
            nullable = false,
            length = 64
    )
    private String barcode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 120)
    private String brand;

    @Column(length = 120)
    private String category;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "target_stock", nullable = false)
    private int targetStock;

    @Column(
            name = "unit_cost",
            precision = 12,
            scale = 2
    )
    private BigDecimal unitCost;

    @Column(
            name = "clover_item_id",
            length = 64
    )
    private String cloverItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductSource source = ProductSource.MANUAL;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
        // JPA requires a no-argument constructor.
    }

    /*
     * Retained temporarily for isolated domain unit tests.
     * Persisted products must use the store-aware constructor.
     */
    public Product(
            String barcode,
            String name,
            String brand,
            String category,
            int quantity,
            int reorderLevel,
            int targetStock,
            BigDecimal unitCost,
            ProductSource source) {

        this.barcode = barcode;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
        this.targetStock = targetStock;
        this.unitCost = unitCost;
        this.source = source == null
                ? ProductSource.MANUAL
                : source;
    }

    public Product(
            Store store,
            String barcode,
            String name,
            String brand,
            String category,
            int quantity,
            int reorderLevel,
            int targetStock,
            BigDecimal unitCost,
            ProductSource source) {

        this(
                barcode,
                name,
                brand,
                category,
                quantity,
                reorderLevel,
                targetStock,
                unitCost,
                source
        );

        assignToStore(store);
    }

    public void assignToStore(Store store) {
        this.store = Objects.requireNonNull(
                store,
                "Store is required"
        );
    }

    @PrePersist
    void beforeInsert() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isLowStock() {
        return quantity <= reorderLevel;
    }

    public int calculateBaseReorderQuantity() {
        if (!isLowStock()) {
            return 0;
        }

        return Math.max(targetStock - quantity, 0);
    }

    public void recordSale(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Sale quantity must be greater than zero"
            );
        }

        if (amount > quantity) {
            throw new IllegalArgumentException(
                    "Sale quantity cannot exceed available inventory"
            );
        }

        quantity -= amount;
    }

    public void restock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Restock quantity must be greater than zero"
            );
        }

        quantity += amount;
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public Long getStoreId() {
        return store == null ? null : store.getId();
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public int getTargetStock() {
        return targetStock;
    }

    public void setTargetStock(int targetStock) {
        this.targetStock = targetStock;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public String getCloverItemId() {
        return cloverItemId;
    }

    public void setCloverItemId(String cloverItemId) {
        this.cloverItemId = cloverItemId;
    }

    public ProductSource getSource() {
        return source;
    }

    public void setSource(ProductSource source) {
        this.source = source;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}