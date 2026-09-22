package com.aieyaan.splynt.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    /*
     * Temporary legacy methods.
     *
     * Existing services and tests currently use these methods.
     * We will remove them after converting the complete application
     * to store-scoped operations.
     */

    Optional<Product> findByBarcode(String barcode);

    Optional<Product> findByCloverItemId(String cloverItemId);

    boolean existsByBarcode(String barcode);

    List<Product> findAllByActiveTrueOrderByNameAsc();

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.active = true
              AND p.quantity <= p.reorderLevel
            ORDER BY p.quantity ASC, p.name ASC
            """)
    List<Product> findLowStockProducts();

    /*
     * SaaS store-scoped methods.
     *
     * These methods prevent one store from accidentally retrieving
     * another store's inventory.
     */

    Optional<Product> findByStoreIdAndId(
            Long storeId,
            Long productId
    );

    Optional<Product> findByStoreIdAndBarcode(
            Long storeId,
            String barcode
    );

    Optional<Product> findByStoreIdAndCloverItemId(
            Long storeId,
            String cloverItemId
    );

    boolean existsByStoreIdAndBarcode(
            Long storeId,
            String barcode
    );

    List<Product> findAllByStoreIdAndActiveTrueOrderByNameAsc(
            Long storeId
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.active = true
              AND p.quantity <= p.reorderLevel
            ORDER BY p.quantity ASC, p.name ASC
            """)
    List<Product> findLowStockProductsByStoreId(
            @Param("storeId") Long storeId
    );
}