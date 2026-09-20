package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndFindsProductByBarcode() {
        Product product = new Product(
                "111111111111",
                "Orange Juice",
                "Example Brand",
                "Beverages",
                10,
                5,
                20,
                new BigDecimal("2.50"),
                ProductSource.MANUAL);

        productRepository.saveAndFlush(product);

        Optional<Product> result =
                productRepository.findByBarcode("111111111111");

        assertTrue(result.isPresent());
        assertEquals("Orange Juice", result.get().getName());
        assertEquals(10, result.get().getQuantity());
    }

    @Test
    void findsOnlyProductsAtOrBelowTheirReorderLevel() {
        Product lowStockProduct = new Product(
                "222222222222",
                "Low Stock Water",
                "Example Brand",
                "Beverages",
                3,
                5,
                20,
                new BigDecimal("1.00"),
                ProductSource.MANUAL);

        Product sufficientlyStockedProduct = new Product(
                "333333333333",
                "Stocked Water",
                "Example Brand",
                "Beverages",
                12,
                5,
                20,
                new BigDecimal("1.00"),
                ProductSource.MANUAL);

        productRepository.saveAllAndFlush(
                List.of(lowStockProduct, sufficientlyStockedProduct));

        List<Product> lowStockProducts =
                productRepository.findLowStockProducts();

        assertEquals(1, lowStockProducts.size());
        assertEquals(
                "Low Stock Water",
                lowStockProducts.getFirst().getName());
    }
}