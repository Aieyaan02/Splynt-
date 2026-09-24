package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieyaan.splynt.product.dto.ProductResponse;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

@ExtendWith(MockitoExtension.class)
class ProductRestoreServiceTest {

    private static final Long STORE_ID = 2L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private Store store;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository,
                storeRepository
        );

        when(storeRepository.findById(STORE_ID))
                .thenReturn(Optional.of(store));

        when(store.isActive()).thenReturn(true);
    }

    @Test
    void returnsArchivedProductsForStore() {
        when(store.getId()).thenReturn(STORE_ID);

        Product archivedProduct = createProduct();
        archivedProduct.setActive(false);

        when(productRepository
                .findAllByStoreIdAndActiveFalseOrderByNameAsc(
                        STORE_ID
                ))
                .thenReturn(List.of(archivedProduct));

        List<ProductResponse> result =
                productService.getArchivedProducts(STORE_ID);

        assertEquals(1, result.size());
        assertEquals(
                "Archived Orange Juice",
                result.getFirst().name()
        );
        assertFalse(result.getFirst().active());

        verify(productRepository)
                .findAllByStoreIdAndActiveFalseOrderByNameAsc(
                        STORE_ID
                );
    }

    @Test
    void restoresArchivedProduct() {
        when(store.getId()).thenReturn(STORE_ID);

        Product archivedProduct = createProduct();
        archivedProduct.setActive(false);

        when(productRepository.findByStoreIdAndId(
                STORE_ID,
                PRODUCT_ID
        ))
                .thenReturn(Optional.of(archivedProduct));

        ProductResponse result = productService.restoreProduct(
                STORE_ID,
                PRODUCT_ID
        );

        assertTrue(archivedProduct.isActive());
        assertTrue(result.active());
        assertEquals(
                "Archived Orange Juice",
                result.name()
        );
    }

    @Test
    void refusesToRestoreActiveProduct() {
        Product activeProduct = createProduct();

        when(productRepository.findByStoreIdAndId(
                STORE_ID,
                PRODUCT_ID
        ))
                .thenReturn(Optional.of(activeProduct));

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.restoreProduct(
                        STORE_ID,
                        PRODUCT_ID
                )
        );
    }

    private Product createProduct() {
        return new Product(
                store,
                "RESTORE-TEST-001",
                "Archived Orange Juice",
                "Example Brand",
                "Beverages",
                8,
                5,
                20,
                new BigDecimal("2.50"),
                ProductSource.MANUAL
        );
    }
}