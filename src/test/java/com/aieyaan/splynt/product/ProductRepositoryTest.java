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

import com.aieyaan.splynt.tenant.Organization;
import com.aieyaan.splynt.tenant.OrganizationRepository;
import com.aieyaan.splynt.tenant.Store;
import com.aieyaan.splynt.tenant.StoreRepository;

@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    void savesAndFindsProductByStoreAndBarcode() {
        Store store = createStore(
                "repository-one",
                "Repository Store One"
        );

        Product product = createProduct(
                store,
                "111111111111",
                "Orange Juice",
                10,
                5
        );

        productRepository.saveAndFlush(product);

        Optional<Product> result =
                productRepository.findByStoreIdAndBarcode(
                        store.getId(),
                        "111111111111"
                );

        assertTrue(result.isPresent());
        assertEquals(
                "Orange Juice",
                result.get().getName()
        );
        assertEquals(10, result.get().getQuantity().intValueExact());
        assertEquals(
                store.getId(),
                result.get().getStoreId()
        );
    }

    @Test
    void findsOnlyLowStockProductsForRequestedStore() {
        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Low Stock Organization",
                                "low-stock-organization"
                        )
                );

        Store firstStore = storeRepository.saveAndFlush(
                new Store(
                        organization,
                        "First Store",
                        "first-store"
                )
        );

        Store secondStore = storeRepository.saveAndFlush(
                new Store(
                        organization,
                        "Second Store",
                        "second-store"
                )
        );

        Product firstStoreLowStock = createProduct(
                firstStore,
                "222222222222",
                "Low Stock Water",
                3,
                5
        );

        Product firstStoreSufficientStock = createProduct(
                firstStore,
                "333333333333",
                "Stocked Water",
                12,
                5
        );

        Product secondStoreLowStock = createProduct(
                secondStore,
                "444444444444",
                "Other Store Water",
                2,
                5
        );

        productRepository.saveAllAndFlush(
                List.of(
                        firstStoreLowStock,
                        firstStoreSufficientStock,
                        secondStoreLowStock
                )
        );

        List<Product> results =
                productRepository
                        .findLowStockProductsByStoreId(
                                firstStore.getId()
                        );

        assertEquals(1, results.size());
        assertEquals(
                "Low Stock Water",
                results.getFirst().getName()
        );
        assertEquals(
                firstStore.getId(),
                results.getFirst().getStoreId()
        );
    }

    @Test
    void allowsSameBarcodeInDifferentStores() {
        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                "Shared Barcode Organization",
                                "shared-barcode-organization"
                        )
                );

        Store firstStore = storeRepository.saveAndFlush(
                new Store(
                        organization,
                        "Downtown Store",
                        "downtown-store"
                )
        );

        Store secondStore = storeRepository.saveAndFlush(
                new Store(
                        organization,
                        "Airport Store",
                        "airport-store"
                )
        );

        Product downtownProduct = createProduct(
                firstStore,
                "049000050103",
                "Downtown Coca-Cola",
                10,
                5
        );

        Product airportProduct = createProduct(
                secondStore,
                "049000050103",
                "Airport Coca-Cola",
                20,
                5
        );

        productRepository.saveAllAndFlush(
                List.of(
                        downtownProduct,
                        airportProduct
                )
        );

        Optional<Product> downtownResult =
                productRepository.findByStoreIdAndBarcode(
                        firstStore.getId(),
                        "049000050103"
                );

        Optional<Product> airportResult =
                productRepository.findByStoreIdAndBarcode(
                        secondStore.getId(),
                        "049000050103"
                );

        assertTrue(downtownResult.isPresent());
        assertTrue(airportResult.isPresent());

        assertEquals(
                "Downtown Coca-Cola",
                downtownResult.get().getName()
        );

        assertEquals(
                "Airport Coca-Cola",
                airportResult.get().getName()
        );
    }

    private Store createStore(
            String slugSuffix,
            String storeName) {

        Organization organization =
                organizationRepository.saveAndFlush(
                        new Organization(
                                storeName + " Organization",
                                slugSuffix + "-organization"
                        )
                );

        return storeRepository.saveAndFlush(
                new Store(
                        organization,
                        storeName,
                        slugSuffix + "-store"
                )
        );
    }

    private Product createProduct(
            Store store,
            String barcode,
            String name,
            int quantity,
            int reorderLevel) {

        return new Product(
                store,
                barcode,
                name,
                "Example Brand",
                "Beverages",
                quantity,
                reorderLevel,
                20,
                new BigDecimal("2.50"),
                ProductSource.MANUAL
        );
    }
}