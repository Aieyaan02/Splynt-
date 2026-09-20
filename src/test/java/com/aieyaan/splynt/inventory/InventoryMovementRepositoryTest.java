package com.aieyaan.splynt.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.product.ProductSource;

@SpringBootTest
@Transactional
class InventoryMovementRepositoryTest {

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndFindsMovementByProduct() {
        Product product = productRepository.saveAndFlush(
                createProduct("222222222222"));

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.RESTOCK,
                5,
                10,
                15,
                InventoryMovementSource.MANUAL,
                "Weekly delivery",
                null);

        movementRepository.saveAndFlush(movement);

        List<InventoryMovement> results =
                movementRepository
                        .findAllByProduct_IdOrderByCreatedAtDesc(
                                product.getId());

        assertEquals(1, results.size());
        assertEquals(
                InventoryMovementType.RESTOCK,
                results.getFirst().getMovementType());
        assertEquals(5, results.getFirst().getQuantityChange());
        assertEquals("Weekly delivery", results.getFirst().getNote());
    }

    @Test
    void findsCloverMovementByExternalReference() {
        Product product = productRepository.saveAndFlush(
                createProduct("333333333333"));

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.SALE,
                -2,
                10,
                8,
                InventoryMovementSource.CLOVER,
                "Imported Clover sale",
                "clover-order-123");

        movementRepository.saveAndFlush(movement);

        boolean exists =
                movementRepository.existsBySourceAndExternalReference(
                        InventoryMovementSource.CLOVER,
                        "clover-order-123");

        assertTrue(exists);
    }

    private Product createProduct(String barcode) {
        return new Product(
                barcode,
                "Test Product",
                "Test Brand",
                "Test Category",
                10,
                5,
                20,
                new BigDecimal("2.50"),
                ProductSource.MANUAL);
    }
}