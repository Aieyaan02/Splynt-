package com.aieyaan.splynt.product;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest
class ProductConcurrencyTest {
    @Autowired ProductRepository products;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;

    @Test
    void rejectsStaleSaleInsteadOfSilentlyLosingStockChanges() {
        String slug = "concurrency-" + UUID.randomUUID();
        Organization organization = organizations.saveAndFlush(new Organization("Test", slug));
        Store store = stores.saveAndFlush(new Store(organization, "Test", slug));
        Product original = products.saveAndFlush(new Product(store, "CONCURRENT", "Water",
                null, null, 10, 3, 20, null, ProductSource.MANUAL));
        try {
            // Separate repository transactions represent two requests reading the same version.
            Product first = products.findById(original.getId()).orElseThrow();
            Product second = products.findById(original.getId()).orElseThrow();
            first.recordSale(3);
            second.recordSale(4);
            products.saveAndFlush(first);
            assertThrows(OptimisticLockingFailureException.class, () -> products.saveAndFlush(second));
            assertEquals(7, products.findById(original.getId()).orElseThrow().getQuantity().intValueExact());
        } finally {
            products.deleteById(original.getId());
            stores.deleteById(store.getId());
            organizations.deleteById(organization.getId());
        }
    }
}
