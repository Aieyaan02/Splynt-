package com.aieyaan.splynt.clover;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.aieyaan.splynt.inventory.*;
import com.aieyaan.splynt.product.*;
import com.aieyaan.splynt.tenant.*;
import tools.jackson.databind.json.JsonMapper;

class CloverInventorySyncServiceTest {
    CloverInventoryClient client = mock(CloverInventoryClient.class);
    ProductRepository products = mock(ProductRepository.class);
    StoreRepository stores = mock(StoreRepository.class);
    InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
    CloverInventorySyncService service = new CloverInventorySyncService(client, products, stores, movements);
    JsonMapper json = new JsonMapper();
    Store store = new Store(new Organization("Shop", "shop"), "Shop", "shop");
    Product product;

    @BeforeEach void setup() {
        product = new Product(store, "ABC", "Water", null, null, 10, 5, 20, null, ProductSource.CLOVER);
        when(stores.findLockedById(1L)).thenReturn(Optional.of(store));
        when(client.getItems(1L)).thenReturn(json.readTree("{\"elements\":[{\"id\":\"item1\",\"name\":\"Water\",\"code\":\"ABC\"}]}"));
        when(products.findByStoreIdAndCloverItemId(1L, "item1")).thenReturn(Optional.of(product));
    }

    void stock(String value) {
        when(client.getItemStocks(1L)).thenReturn(json.readTree("{\"elements\":[{\"item\":{\"id\":\"item1\"},\"quantity\":" + value + "}]}"));
    }

    @Test void reconcilesWithoutInventingSales() {
        stock("3");
        service.synchronize(1L);
        assertEquals(3, product.getQuantity());
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements).save(captor.capture());
        assertEquals(InventoryMovementType.ADJUSTMENT, captor.getValue().getMovementType());
        assertEquals(InventoryMovementSource.CLOVER, captor.getValue().getSource());
        assertEquals(10, captor.getValue().getQuantityBefore());
        assertEquals(3, captor.getValue().getQuantityAfter());
    }

    @Test void missingStockDoesNotEraseKnownQuantity() {
        when(client.getItemStocks(1L)).thenReturn(json.readTree("{\"elements\":[]}"));
        assertEquals(1, service.synchronize(1L).skipped());
        assertEquals(10, product.getQuantity());
        verifyNoInteractions(movements);
        verify(products, never()).save(any());
    }

    @Test void malformedStockFailsInsteadOfZeroingCatalog() {
        when(client.getItemStocks(1L)).thenReturn(json.readTree("{}"));
        assertThrows(IllegalStateException.class, () -> service.synchronize(1L));
        verifyNoInteractions(movements);
    }

    @Test void preservesIntentionalArchive() {
        stock("3");
        product.setActive(false);
        assertEquals(1, service.synchronize(1L).skipped());
        assertFalse(product.isActive());
        verifyNoInteractions(movements);
    }

    @Test void unchangedSnapshotDoesNotDuplicateHistory() {
        stock("10");
        service.synchronize(1L);
        verifyNoInteractions(movements);
    }

    @Test void fractionalAndNegativeStockAreNotSilentlyCoerced() {
        for (String amount : new String[] {"1.5", "-3", "null", "2147483648"}) {
            stock(amount);
            assertEquals(1, service.synchronize(1L).skipped());
        }
        assertEquals(10, product.getQuantity());
        verifyNoInteractions(movements);
    }
}
