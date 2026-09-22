package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;
import com.aieyaan.splynt.tenant.exception.StoreNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createsProductForStoreAndNormalizesText() {
        Store store = activeStore(10L);

        when(store.getId()).thenReturn(10L);

        CreateProductRequest request = new CreateProductRequest(
                " 123456789012 ",
                " Orange Juice ",
                " Example Brand ",
                " Beverages ",
                10,
                5,
                20,
                new BigDecimal("2.50")
        );

        when(productRepository.existsByStoreIdAndBarcode(
                10L,
                "123456789012"
        )).thenReturn(false);

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0, Product.class)
                );

        ProductResponse response =
                productService.createProduct(10L, request);

        assertEquals(10L, response.storeId());
        assertEquals("123456789012", response.barcode());
        assertEquals("Orange Juice", response.name());
        assertEquals(
                ProductSource.MANUAL,
                response.source()
        );

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(
                productCaptor.capture()
        );

        Product savedProduct = productCaptor.getValue();

        assertSame(store, savedProduct.getStore());
        assertEquals(
                "Example Brand",
                savedProduct.getBrand()
        );
        assertEquals(
                "Beverages",
                savedProduct.getCategory()
        );
    }

    @Test
    void rejectsDuplicateBarcodeInsideSameStore() {
        activeStore(10L);

        CreateProductRequest request = validRequest();

        when(productRepository.existsByStoreIdAndBarcode(
                10L,
                "123456789012"
        )).thenReturn(true);

        DuplicateProductException exception = assertThrows(
                DuplicateProductException.class,
                () -> productService.createProduct(
                        10L,
                        request
                )
        );

        assertEquals(
                "A product with barcode 123456789012 "
                        + "already exists in store 10",
                exception.getMessage()
        );

        verify(productRepository, never())
                .save(any(Product.class));
    }

    @Test
    void rejectsTargetStockBelowReorderLevel() {
        CreateProductRequest request =
                new CreateProductRequest(
                        "123456789012",
                        "Orange Juice",
                        "Example Brand",
                        "Beverages",
                        10,
                        5,
                        4,
                        new BigDecimal("2.50")
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.createProduct(
                        10L,
                        request
                )
        );

        assertEquals(
                "Target stock cannot be lower than "
                        + "the reorder level",
                exception.getMessage()
        );

        verifyNoInteractions(productRepository);
        verifyNoInteractions(storeRepository);
    }

    @Test
    void rejectsMissingStoreWhenCreatingProduct() {
        when(storeRepository.findById(999L))
                .thenReturn(Optional.empty());

        StoreNotFoundException exception = assertThrows(
                StoreNotFoundException.class,
                () -> productService.createProduct(
                        999L,
                        validRequest()
                )
        );

        assertEquals(
                "Active store with ID 999 was not found",
                exception.getMessage()
        );

        verifyNoInteractions(productRepository);
    }

    @Test
    void reportsMissingProductInsideStore() {
        activeStore(10L);

        when(productRepository.findByStoreIdAndId(
                10L,
                999L
        )).thenReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductById(
                        10L,
                        999L
                )
        );

        assertEquals(
                "Product with ID 999 was not found in store 10",
                exception.getMessage()
        );
    }

    private Store activeStore(Long storeId) {
        Store store = mock(Store.class);

        when(storeRepository.findById(storeId))
                .thenReturn(Optional.of(store));

        when(store.isActive()).thenReturn(true);

        return store;
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
                new BigDecimal("2.50")
        );
    }
}