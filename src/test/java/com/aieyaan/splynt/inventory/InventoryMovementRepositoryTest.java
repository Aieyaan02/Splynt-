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
import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

@SpringBootTest
@Transactional
class InventoryMovementRepositoryTest {

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    void savesAndFindsMovementByStoreAndProduct() {
        Product product = productRepository.saveAndFlush(
                createProduct("222222222222")
        );

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.RESTOCK,
                5,
                10,
                15,
                InventoryMovementSource.MANUAL,
                "Weekly delivery",
                null
        );

        movementRepository.saveAndFlush(movement);

        List<InventoryMovement> results =
                movementRepository
                        .findAllByProduct_Store_IdAndProduct_IdOrderByCreatedAtDesc(
                                product.getStoreId(),
                                product.getId()
                        );

        assertEquals(1, results.size());

        assertEquals(
                InventoryMovementType.RESTOCK,
                results.getFirst().getMovementType()
        );

        assertEquals(
                5,
                results.getFirst().getQuantityChange()
        );

        assertEquals(
                "Weekly delivery",
                results.getFirst().getNote()
        );
    }

    @Test
    void findsCloverMovementByExternalReference() {
        Product product = productRepository.saveAndFlush(
                createProduct("333333333333")
        );

        InventoryMovement movement = new InventoryMovement(
                product,
                InventoryMovementType.SALE,
                -2,
                10,
                8,
                InventoryMovementSource.CLOVER,
                "Imported Clover sale",
                "clover-order-123"
        );

        movementRepository.saveAndFlush(movement);

        boolean exists =
                movementRepository
                        .existsBySourceAndExternalReference(
                                InventoryMovementSource.CLOVER,
                                "clover-order-123"
                        );

        assertTrue(exists);
    }

    private Product createProduct(String barcode) {
        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Inventory " + barcode,
                                "inventory-" + barcode
                        )
                );

        Store store = storeRepository.saveAndFlush(
                new Store(
                        organization,
                        "Inventory Store " + barcode,
                        "inventory-store-" + barcode
                )
        );

        return new Product(
                store,
                barcode,
                "Test Product",
                "Test Brand",
                "Test Category",
                10,
                5,
                20,
                new BigDecimal("2.50"),
                ProductSource.MANUAL
        );
    }
}