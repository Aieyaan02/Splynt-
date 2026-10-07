package com.aieyaan.splynt.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.product.ProductSource;

class InventoryMovementTest {

    @Test
    void createsValidSaleMovement() {
        Product product = createProduct();

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.SALE,
                -3,
                10,
                7,
                InventoryMovementSource.MANUAL,
                " Customer purchase ",
                null);

        assertEquals(
                InventoryMovementType.SALE,
                movement.getMovementType());

        assertEquals(-3, movement.getQuantityChange().intValueExact());
        assertEquals(10, movement.getQuantityBefore().intValueExact());
        assertEquals(7, movement.getQuantityAfter().intValueExact());
        assertEquals("Customer purchase", movement.getNote());
    }

    @Test
    void rejectsSaleThatIncreasesInventory() {
        Product product = createProduct();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryMovement(
                        product,
                        InventoryMovementType.SALE,
                        3,
                        10,
                        13,
                        InventoryMovementSource.MANUAL,
                        null,
                        null));

        assertEquals(
                "A sale must decrease inventory",
                exception.getMessage());
    }

    @Test
    void rejectsInconsistentInventoryBalance() {
        Product product = createProduct();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryMovement(
                        product,
                        InventoryMovementType.RESTOCK,
                        5,
                        10,
                        20,
                        InventoryMovementSource.MANUAL,
                        null,
                        null));

        assertEquals(
                "Quantity after must equal quantity before plus the change",
                exception.getMessage());
    }

    private Product createProduct() {
        return new Product(
                "111111111111",
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