package com.aieyaan.splynt.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;
import com.aieyaan.splynt.inventory.dto.InventoryMovementResponse;
import com.aieyaan.splynt.inventory.dto.InventoryTransactionResponse;
import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.product.ProductSource;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void recordSaleDecreasesInventoryAndCreatesMovement() {
        Product product = createProduct();

        when(productRepository.findByStoreIdAndId(
                10L,
                1L
        )).thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(movementRepository.saveAndFlush(
                any(InventoryMovement.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        InventoryChangeRequest request =
                new InventoryChangeRequest(
                        3,
                        "Customer purchase"
                );

        InventoryTransactionResponse response =
                inventoryService.recordSale(
                        10L,
                        1L,
                        request
                );

        assertEquals(7, response.product().quantity().intValueExact());

        assertEquals(
                InventoryMovementType.SALE,
                response.movement().movementType()
        );

        assertEquals(
                -3,
                response.movement().quantityChange().intValueExact()
        );

        assertEquals(
                10,
                response.movement().quantityBefore().intValueExact()
        );

        assertEquals(
                7,
                response.movement().quantityAfter().intValueExact()
        );

        assertEquals(
                InventoryMovementSource.MANUAL,
                response.movement().source()
        );

        assertEquals(
                "Customer purchase",
                response.movement().note()
        );

        verify(productRepository)
                .findByStoreIdAndId(10L, 1L);

        verify(productRepository).save(product);

        verify(movementRepository)
                .saveAndFlush(any(InventoryMovement.class));
    }

    @Test
    void recordRestockIncreasesInventoryAndCreatesMovement() {
        Product product = createProduct();

        when(productRepository.findByStoreIdAndId(
                10L,
                1L
        )).thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(movementRepository.saveAndFlush(
                any(InventoryMovement.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        InventoryChangeRequest request =
                new InventoryChangeRequest(
                        5,
                        "Supplier delivery"
                );

        InventoryTransactionResponse response =
                inventoryService.recordRestock(
                        10L,
                        1L,
                        request
                );

        assertEquals(15, response.product().quantity().intValueExact());

        assertEquals(
                InventoryMovementType.RESTOCK,
                response.movement().movementType()
        );

        assertEquals(
                5,
                response.movement().quantityChange().intValueExact()
        );

        assertEquals(
                10,
                response.movement().quantityBefore().intValueExact()
        );

        assertEquals(
                15,
                response.movement().quantityAfter().intValueExact()
        );

        assertEquals(
                "Supplier delivery",
                response.movement().note()
        );

        verify(productRepository)
                .findByStoreIdAndId(10L, 1L);

        verify(productRepository).save(product);

        verify(movementRepository)
                .saveAndFlush(any(InventoryMovement.class));
    }

    @Test
    void recordSaleRejectsQuantityGreaterThanAvailableInventory() {
        Product product = createProduct();

        when(productRepository.findByStoreIdAndId(
                10L,
                1L
        )).thenReturn(Optional.of(product));

        InventoryChangeRequest request =
                new InventoryChangeRequest(
                        11,
                        "Too many items"
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.recordSale(
                        10L,
                        1L,
                        request
                )
        );

        assertEquals(
                "Sale quantity cannot exceed available inventory",
                exception.getMessage()
        );

        assertEquals(10, product.getQuantity().intValueExact());

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(movementRepository);
    }

    @Test
    void recordSaleRejectsProductFromAnotherStore() {
        when(productRepository.findByStoreIdAndId(
                10L,
                99L
        )).thenReturn(Optional.empty());

        InventoryChangeRequest request =
                new InventoryChangeRequest(
                        1,
                        "Customer purchase"
                );

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> inventoryService.recordSale(
                        10L,
                        99L,
                        request
                )
        );

        assertEquals(
                "Product with ID 99 was not found in store 10",
                exception.getMessage()
        );

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(movementRepository);
    }

    @Test
    void retrievesOnlyStoreScopedMovementHistory() {
        Product product = createProduct();

        when(productRepository.findByStoreIdAndId(
                10L,
                1L
        )).thenReturn(Optional.of(product));

        when(movementRepository
                .findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(
                        10L,
                        1L
                ))
                .thenReturn(List.of());

        List<InventoryMovementResponse> response =
                inventoryService.getMovementHistory(
                        10L,
                        1L
                );

        assertEquals(0, response.size());

        verify(movementRepository)
                .findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(
                        10L,
                        1L
                );
    }

    private Product createProduct() {
        return new Product(
                "123456789012",
                "Orange Juice",
                "Example Brand",
                "Beverages",
                10,
                5,
                20,
                new BigDecimal("2.50"),
                ProductSource.MANUAL
        );
    }
}