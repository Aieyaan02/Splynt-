package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(
                "012345678901",
                "Test Beverage",
                "Test Brand",
                "Beverages",
                10,
                5,
                20,
                new BigDecimal("1.50"),
                ProductSource.MANUAL);
    }

    @Test
    void recordSaleReducesInventory() {
        product.recordSale(3);

        assertEquals(7, product.getQuantity());
    }

    @Test
    void recordSaleRejectsInsufficientInventory() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> product.recordSale(11));

        assertEquals(
                "Sale quantity cannot exceed available inventory",
                exception.getMessage());
    }

    @Test
    void restockIncreasesInventory() {
        product.restock(8);

        assertEquals(18, product.getQuantity());
    }

    @Test
    void lowStockProductCalculatesBaseReorderQuantity() {
        product.recordSale(7);

        assertTrue(product.isLowStock());
        assertEquals(17, product.calculateBaseReorderQuantity());
    }
}