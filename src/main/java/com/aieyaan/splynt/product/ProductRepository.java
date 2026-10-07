package com.aieyaan.splynt.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.store.id = :storeId and p.id = :productId")
    Optional<Product> findLockedByStoreIdAndId(@Param("storeId") Long storeId, @Param("productId") Long productId);

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.id = :productId
            """)
    Optional<Product> findByStoreIdAndId(
            @Param("storeId") Long storeId,
            @Param("productId") Long productId
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.barcode = :barcode
            """)
    Optional<Product> findByStoreIdAndBarcode(
            @Param("storeId") Long storeId,
            @Param("barcode") String barcode
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.cloverItemId = :cloverItemId
            """)
    Optional<Product> findByStoreIdAndCloverItemId(
            @Param("storeId") Long storeId,
            @Param("cloverItemId") String cloverItemId
    );

    @Query("select case when count(p) > 0 then true else false end from Product p where p.store.id = :storeId")
    boolean existsByStoreId(@Param("storeId") Long storeId);

    @Query("""
            SELECT CASE
                WHEN COUNT(p) > 0 THEN true
                ELSE false
            END
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.barcode = :barcode
            """)
    boolean existsByStoreIdAndBarcode(
            @Param("storeId") Long storeId,
            @Param("barcode") String barcode
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.active = true
            ORDER BY p.name ASC
            """)
    List<Product> findAllByStoreIdAndActiveTrueOrderByNameAsc(
            @Param("storeId") Long storeId
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.active = false
            ORDER BY p.name ASC
            """)
    List<Product> findAllByStoreIdAndActiveFalseOrderByNameAsc(
            @Param("storeId") Long storeId
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.store.id = :storeId
              AND p.active = true
              AND p.stockKnown = true
              AND p.quantity <= p.reorderLevel
            ORDER BY p.quantity ASC, p.name ASC
            """)
    List<Product> findLowStockProductsByStoreId(
            @Param("storeId") Long storeId
    );
}