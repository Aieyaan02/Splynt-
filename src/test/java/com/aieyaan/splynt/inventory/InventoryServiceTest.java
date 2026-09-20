package com.aieyaan.splynt.inventory;

import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;
import com.aieyaan.splynt.inventory.dto.InventoryTransactionResponse;
import com.aieyaan.splynt.product.Product;
import com.aieyaan.splynt.product.ProductRepository;
import com.aieyaan.splynt.product.ProductSource;
import com.aieyaan.splynt.product.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(movementRepository.saveAndFlush(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryChangeRequest request =
                new InventoryChangeRequest(3, "Customer purchase");

        InventoryTransactionResponse response =
                inventoryService.recordSale(1L, request);

        assertEquals(7, response.product().quantity());

        assertEquals(
                InventoryMovementType.SALE,
                response.movement().movementType()
        );

        assertEquals(-3, response.movement().quantityChange());
        assertEquals(10, response.movement().quantityBefore());
        assertEquals(7, response.movement().quantityAfter());

        assertEquals(
                InventoryMovementSource.MANUAL,
                response.movement().source()
        );

        assertEquals(
                "Customer purchase",
                response.movement().note()
        );

        verify(productRepository).save(product);

        verify(movementRepository)
                .saveAndFlush(any(InventoryMovement.class));
    }

    @Test
    void recordRestockIncreasesInventoryAndCreatesMovement() {
        Product product = createProduct();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(movementRepository.saveAndFlush(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryChangeRequest request =
                new InventoryChangeRequest(5, "Supplier delivery");

        InventoryTransactionResponse response =
                inventoryService.recordRestock(1L, request);

        assertEquals(15, response.product().quantity());

        assertEquals(
                InventoryMovementType.RESTOCK,
                response.movement().movementType()
        );

        assertEquals(5, response.movement().quantityChange());
        assertEquals(10, response.movement().quantityBefore());
        assertEquals(15, response.movement().quantityAfter());

        assertEquals(
                InventoryMovementSource.MANUAL,
                response.movement().source()
        );

        assertEquals(
                "Supplier delivery",
                response.movement().note()
        );

        verify(productRepository).save(product);

        verify(movementRepository)
                .saveAndFlush(any(InventoryMovement.class));
    }

    @Test
    void recordSaleRejectsQuantityGreaterThanAvailableInventory() {
        Product product = createProduct();

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        InventoryChangeRequest request =
                new InventoryChangeRequest(11, "Too many items");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.recordSale(1L, request)
        );

        assertEquals(
                "Sale quantity cannot exceed available inventory",
                exception.getMessage()
        );

        assertEquals(10, product.getQuantity());

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(movementRepository);
    }

    @Test
    void recordSaleThrowsExceptionWhenProductDoesNotExist() {
        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        InventoryChangeRequest request =
                new InventoryChangeRequest(1, "Customer purchase");

        assertThrows(
                ProductNotFoundException.class,
                () -> inventoryService.recordSale(99L, request)
        );

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(movementRepository);
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