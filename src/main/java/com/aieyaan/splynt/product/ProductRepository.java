package com.aieyaan.splynt.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

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
}