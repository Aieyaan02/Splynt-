package com.aieyaan.splynt.insights;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.aieyaan.splynt.clover.*;
import com.aieyaan.splynt.product.*;
import com.aieyaan.splynt.tenant.*;
import tools.jackson.databind.json.JsonMapper;
@SpringBootTest @Transactional
class CloverSalesSyncServiceTest {
    @MockitoBean CloverInventoryClient client;
    @Autowired CloverSalesSyncService sync;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired OrganizationRepository organizations;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;
    @Autowired SalesEventRepository sales;
    JsonMapper json = new JsonMapper();
    Store store;
    Product product;
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        var org = organizations.saveAndFlush(new Organization("Sales", suffix));
        store = stores.saveAndFlush(new Store(org, "Sales", suffix));
        product = new Product(store, "ABC", "Coffee", null, null, 10, 5, 20, null, ProductSource.CLOVER);
        product.setCloverItemId("item1"); product = products.saveAndFlush(product);
        var credential = new CloverOAuthCredential(suffix, "unused-encrypted", "unused-encrypted", null, null);
        credential.assignStore(store.getId()); credentials.saveAndFlush(credential);
        orders("PAID");
        when(client.getOrderLines(store.getId(), "order1")).thenReturn(json.readTree(
                "{\"elements\":[{\"id\":\"line1\",\"item\":{\"id\":\"item1\"}},{\"id\":\"line2\",\"item\":{\"id\":\"item1\"}}]}"));
    }
    void orders(String state) {
        when(client.getOrdersModifiedSince(eq(store.getId()), anyLong())).thenReturn(json.readTree(
                "{\"elements\":[{\"id\":\"order1\",\"paymentState\":\"" + state + "\",\"createdTime\":"
                + OffsetDateTime.now().minusDays(2).toInstant().toEpochMilli() + "}]}"));
    }
    @Test void repeatedImportIsIdempotentAndDoesNotTouchStock() {
        sync.synchronize(store.getId()); sync.synchronize(store.getId());
        var events = sales.findAllByStoreIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(store.getId(), OffsetDateTime.now().minusDays(90));
        assertEquals(2, events.size());
        assertEquals(0, events.getFirst().getUnits().compareTo(BigDecimal.ONE));
        assertEquals(10, products.findById(product.getId()).orElseThrow().getQuantity().intValueExact());
    }
    @Test void laterRefundRemovesPreviouslyCountedSales() {
        sync.synchronize(store.getId()); orders("REFUNDED"); sync.synchronize(store.getId());
        assertTrue(sales.findAllByStoreIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(store.getId(), OffsetDateTime.now().minusDays(90)).isEmpty());
    }
    @Test void unknownProductCannotLeakOtherStoresCatalogIntoSales() {
        when(client.getOrderLines(store.getId(), "order1")).thenReturn(json.readTree("{\"elements\":[{\"id\":\"line1\",\"item\":{\"id\":\"other-store-item\"}}]}"));
        sync.synchronize(store.getId());
        assertTrue(sales.findAllByStoreIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(store.getId(), OffsetDateTime.now().minusDays(90)).isEmpty());
        assertNotNull(credentials.findByStoreId(store.getId()).orElseThrow().getSalesSyncError());
    }
    @Test void ordinaryQuantityDoesNotMultiplyByFixedPointField() {
        assertEquals(0, CloverSalesSyncService.units(json.readTree("{\"unitQty\":1000}")).compareTo(BigDecimal.ONE));
        assertEquals(0, CloverSalesSyncService.units(json.readTree("{\"unitQty\":2500,\"unitName\":\"oz\"}")).compareTo(new BigDecimal("2.5")));
        assertEquals(0, CloverSalesSyncService.units(json.readTree("{\"quantitySold\":3}")).compareTo(new BigDecimal("3")));
        assertNull(CloverSalesSyncService.units(json.readTree("{\"quantitySold\":-1}")));
    }
    @Test void unresolvedPaidLinesRetryOriginalWindowUntilCatalogMatchReturns() {
        when(client.getOrderLines(store.getId(), "order1")).thenReturn(json.readTree("{\"elements\":[{\"id\":\"line1\",\"item\":{\"id\":\"missing\"}}]}"));
        sync.synchronize(store.getId());
        var connection = credentials.findByStoreId(store.getId()).orElseThrow();
        var retryFrom = connection.getSalesRetryFrom();
        assertNotNull(retryFrom); assertNotNull(connection.getSalesSyncError());
        sync.synchronize(store.getId());
        assertEquals(retryFrom, connection.getSalesRetryFrom());
        verify(client, times(2)).getOrdersModifiedSince(store.getId(), retryFrom.toInstant().toEpochMilli());
        when(client.getOrderLines(store.getId(), "order1")).thenReturn(json.readTree("{\"elements\":[{\"id\":\"line1\",\"item\":{\"id\":\"item1\"}}]}"));
        sync.synchronize(store.getId());
        assertNull(connection.getSalesRetryFrom()); assertNull(connection.getSalesSyncError());
        assertEquals(1, sales.findAllByStoreIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(store.getId(), retryFrom).size());
    }
    @Test void intentionalUnpaidAndRefundedExclusionsDoNotBlockAdvice() {
        for (String state : new String[]{"OPEN", "REFUNDED", "PARTIALLY_REFUNDED"}) {
            orders(state); sync.synchronize(store.getId());
            var connection = credentials.findByStoreId(store.getId()).orElseThrow();
            assertNull(connection.getSalesSyncError()); assertNull(connection.getSalesRetryFrom());
        }
    }

}
