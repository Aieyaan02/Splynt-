package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieyaan.splynt.product.dto.CreateProductRequest;
import com.aieyaan.splynt.product.dto.ProductResponse;
import com.aieyaan.splynt.product.exception.DuplicateProductException;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createsManualProductAndNormalizesText() {
        CreateProductRequest request = new CreateProductRequest(
                " 123456789012 ",
                " Orange Juice ",
                " Example Brand ",
                " Beverages ",
                10,
                5,
                20,
                new BigDecimal("2.50"));

        when(productRepository.existsByBarcode("123456789012"))
                .thenReturn(false);

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0, Product.class));

        ProductResponse response = productService.createProduct(request);

        assertEquals("123456789012", response.barcode());
        assertEquals("Orange Juice", response.name());
        assertEquals(ProductSource.MANUAL, response.source());

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();

        assertEquals("Example Brand", savedProduct.getBrand());
        assertEquals("Beverages", savedProduct.getCategory());
    }

    @Test
    void rejectsDuplicateBarcode() {
        CreateProductRequest request = validRequest();

        when(productRepository.existsByBarcode("123456789012"))
                .thenReturn(true);

        DuplicateProductException exception = assertThrows(
                DuplicateProductException.class,
                () -> productService.createProduct(request));

        assertEquals(
                "A product with barcode 123456789012 already exists",
                exception.getMessage());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void rejectsTargetStockBelowReorderLevel() {
        CreateProductRequest request = new CreateProductRequest(
                "123456789012",
                "Orange Juice",
                "Example Brand",
                "Beverages",
                10,
                5,
                4,
                new BigDecimal("2.50"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.createProduct(request));

        assertEquals(
                "Target stock cannot be lower than the reorder level",
                exception.getMessage());

        verifyNoInteractions(productRepository);
    }

    @Test
    void reportsMissingProductById() {
        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductById(999L));

        assertEquals(
                "Product with ID 999 was not found",
                exception.getMessage());
    }

    private CreateProductRequest validRequest() {
        return new CreateProductRequest(
                "123456789012",
                "Orange Juice",
                "Example Brand",
                "Beverages",
                10,
                5,
                20,
                new BigDecimal("2.50"));
    }
}