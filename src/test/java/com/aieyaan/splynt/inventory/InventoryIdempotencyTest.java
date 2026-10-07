package com.aieyaan.splynt.inventory;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.aieyaan.splynt.inventory.dto.InventoryChangeRequest;
import com.aieyaan.splynt.product.*;
import com.aieyaan.splynt.tenant.*;

@SpringBootTest
class InventoryIdempotencyTest {
    @Autowired InventoryService service;
    @Autowired ProductRepository products;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;

    private Product product() {
        String suffix = UUID.randomUUID().toString();
        var org = organizations.saveAndFlush(new Organization("Retry test", "retry-" + suffix));
        var store = stores.saveAndFlush(new Store(org, "Retry store", "retry-" + suffix));
        return products.saveAndFlush(new Product(store, suffix, "Juice", "Brand", "Drinks",
                10, 5, 20, new BigDecimal("2.50"), ProductSource.MANUAL));
    }
    private InventoryChangeRequest request(String id, String amount, String note) {
        return new InventoryChangeRequest(new BigDecimal(amount), note, id);
    }

    @Test void replayDoesNotApplyAgainEvenAfterAnotherMovement() {
        var p = product();
        String id = UUID.randomUUID().toString();
        var original = service.recordSale(p.getStoreId(), p.getId(), request(id, "3", " sale "));
        service.recordRestock(p.getStoreId(), p.getId(), request(UUID.randomUUID().toString(), "5", null));
        var replay = service.recordSale(p.getStoreId(), p.getId(), request(id, "3.000", "sale"));
        assertEquals(original.movement().id(), replay.movement().id());
        assertEquals(12, replay.product().quantity().intValueExact());
        assertEquals(2, service.getMovementHistory(p.getStoreId(), p.getId()).size());
    }

    @Test void reusedKeyRejectsChangedQuantityNoteOrOperation() {
        var p = product();
        String id = UUID.randomUUID().toString();
        service.recordRestock(p.getStoreId(), p.getId(), request(id, "2", null));
        assertThrows(IllegalArgumentException.class, () -> service.recordRestock(p.getStoreId(), p.getId(), request(id, "3", null)));
        assertThrows(IllegalArgumentException.class, () -> service.recordRestock(p.getStoreId(), p.getId(), request(id, "2", "changed")));
        assertThrows(IllegalArgumentException.class, () -> service.recordSale(p.getStoreId(), p.getId(), request(id, "2", null)));
        assertEquals(12, products.findById(p.getId()).orElseThrow().getQuantity().intValueExact());
        assertEquals(1, service.getMovementHistory(p.getStoreId(), p.getId()).size());
    }

    @Test void simultaneousRetriesCreateOneMovement() throws Exception {
        var p = product();
        var request = request(UUID.randomUUID().toString(), "4", null);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Long> call = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.recordSale(p.getStoreId(), p.getId(), request).movement().id();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(6, products.findById(p.getId()).orElseThrow().getQuantity().intValueExact());
        assertEquals(1, service.getMovementHistory(p.getStoreId(), p.getId()).size());
    }
}
