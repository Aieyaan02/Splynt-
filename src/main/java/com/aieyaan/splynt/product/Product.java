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

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "stock_known", nullable = false)
    private boolean stockKnown = true;
    public boolean isStockKnown() { return stockKnown; }
    public void markStockUnknown() { stockKnown = false; }
    public void reconcileStock(BigDecimal amount) { quantity = stockAmount(amount); stockKnown = true; }

    @Column(name = "reorder_level", nullable = false, precision = 18, scale = 6)
    private BigDecimal reorderLevel;

    @Column(name = "target_stock", nullable = false, precision = 18, scale = 6)
    private BigDecimal targetStock;

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

    @Column(name = "clover_details_json", columnDefinition = "text")
    private String cloverDetailsJson;

    public CloverCatalogDetails getCloverDetails() {
        return cloverDetailsJson == null ? null : new tools.jackson.databind.json.JsonMapper().readValue(cloverDetailsJson, CloverCatalogDetails.class);
    }

    public void setCloverDetails(CloverCatalogDetails details) {
        cloverDetailsJson = details == null ? null : new tools.jackson.databind.json.JsonMapper().writeValueAsString(details);
    }

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
            BigDecimal quantity,
            BigDecimal reorderLevel,
            BigDecimal targetStock,
            BigDecimal unitCost,
            ProductSource source) {

        this.barcode = barcode;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.quantity = stockAmount(quantity);
        this.reorderLevel = stockAmount(reorderLevel);
        this.targetStock = stockAmount(targetStock);
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
            BigDecimal quantity,
            BigDecimal reorderLevel,
            BigDecimal targetStock,
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

    public Product(String barcode, String name, String brand, String category, int quantity, int reorderLevel, int targetStock, BigDecimal unitCost, ProductSource source) {
        this(barcode, name, brand, category, BigDecimal.valueOf(quantity), BigDecimal.valueOf(reorderLevel), BigDecimal.valueOf(targetStock), unitCost, source);
    }
    public Product(Store store, String barcode, String name, String brand, String category, int quantity, int reorderLevel, int targetStock, BigDecimal unitCost, ProductSource source) {
        this(store, barcode, name, brand, category, BigDecimal.valueOf(quantity), BigDecimal.valueOf(reorderLevel), BigDecimal.valueOf(targetStock), unitCost, source);
    }
    public void setReorderLevel(int value) { setReorderLevel(BigDecimal.valueOf(value)); }
    public void setTargetStock(int value) { setTargetStock(BigDecimal.valueOf(value)); }

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

    public static BigDecimal stockAmount(BigDecimal value) {
        Objects.requireNonNull(value, "Stock amount is required");
        try {
            BigDecimal amount = value.setScale(6, java.math.RoundingMode.UNNECESSARY);
            if (amount.precision() > 18) throw new IllegalArgumentException("Stock amount is too large");
            return amount.stripTrailingZeros();
        } catch (ArithmeticException ex) { throw new IllegalArgumentException("Stock supports up to six decimal places"); }
    }
    public boolean isLowStock() { return stockKnown && quantity.compareTo(reorderLevel) <= 0; }
    public BigDecimal calculateBaseReorderQuantity() {
        return isLowStock() ? targetStock.subtract(quantity).max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }
    public void recordSale(int amount) { recordSale(BigDecimal.valueOf(amount)); }
    public void recordSale(BigDecimal amount) {
        amount = stockAmount(amount);
        if (!stockKnown) throw new IllegalArgumentException("Stock is unknown; synchronize it before recording changes");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Sale quantity must be greater than zero");
        if (amount.compareTo(quantity) > 0) throw new IllegalArgumentException("Sale quantity cannot exceed available inventory");
        quantity = stockAmount(quantity.subtract(amount));
    }
    public void restock(int amount) { restock(BigDecimal.valueOf(amount)); }
    public void restock(BigDecimal amount) {
        amount = stockAmount(amount);
        if (!stockKnown) throw new IllegalArgumentException("Stock is unknown; synchronize it before recording changes");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Restock quantity must be greater than zero");
        quantity = stockAmount(quantity.add(amount));
    }

    public long getVersion() { return version; }

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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(BigDecimal reorderLevel) {
        this.reorderLevel = stockAmount(reorderLevel);
    }

    public BigDecimal getTargetStock() {
        return targetStock;
    }

    public void setTargetStock(BigDecimal targetStock) {
        this.targetStock = stockAmount(targetStock);
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