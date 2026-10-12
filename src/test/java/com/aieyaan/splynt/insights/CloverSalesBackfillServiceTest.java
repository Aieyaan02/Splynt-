package com.aieyaan.splynt.insights;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.aieyaan.splynt.clover.*;
import com.aieyaan.splynt.product.*;
import com.aieyaan.splynt.tenant.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class CloverSalesBackfillServiceTest {
    @MockitoBean CloverInventoryClient client;
    @Autowired CloverSalesBackfillService backfill;
    @Autowired CloverOAuthCredentialRepository credentials;
    @Autowired StoreRepository stores;
    @Autowired OrganizationRepository organizations;
    @Autowired ProductRepository products;
    @Autowired SalesEventRepository sales;
    final JsonMapper json = new JsonMapper();
    Store store;
    Product product;
    OffsetDateTime recent = OffsetDateTime.now().withNano(0).minusDays(90);
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        var org = organizations.saveAndFlush(new Organization("History", suffix));
        store = stores.saveAndFlush(new Store(org, "History", suffix));
        product = new Product(store, "ABC", "Coffee", null, null, 10, 5, 20, null, ProductSource.CLOVER);
        product.setCloverItemId("item1"); product = products.saveAndFlush(product);
        var connection = new CloverOAuthCredential(suffix, "unused", "unused", null, null);
        connection.assignStore(store.getId());
        connection.markSalesSynchronized(recent.plusDays(90), recent, 0);
        connection.requestSalesBackfill(recent.minusDays(45));
        credentials.saveAndFlush(connection);
        when(client.getOrdersCreatedBetween(eq(store.getId()), anyLong(), anyLong())).thenAnswer(call -> json.readTree(
                "{\"elements\":[{\"id\":\"order-" + call.getArgument(1) + "\",\"createdTime\":" + call.getArgument(1) + ",\"paymentState\":\"PAID\"}]}"));
        when(client.getOrderLines(eq(store.getId()), anyString())).thenReturn(json.readTree(
                "{\"elements\":[{\"id\":\"line1\",\"item\":{\"id\":\"item1\"}}]}"));
    }
    CloverOAuthCredential connection() { return credentials.findByStoreId(store.getId()).orElseThrow(); }
    @Test void chunksResumeAndPreserveRecentWatermarkAndStock() {
        var synced = connection().getSalesSyncedAt();
        backfill.step(store.getId());
        assertEquals(recent.minusDays(30).toInstant(), connection().getSalesCoverageStart().toInstant());
        assertNotNull(connection().getSalesBackfillTarget());
        backfill.step(store.getId());
        assertEquals(recent.minusDays(45).toInstant(), connection().getSalesCoverageStart().toInstant());
        assertNull(connection().getSalesBackfillTarget());
        assertEquals(synced.toInstant(), connection().getSalesSyncedAt().toInstant());
        assertEquals(2, sales.findWindow(store.getId(), recent.minusDays(46), recent).size());
        assertEquals(10, products.findById(product.getId()).orElseThrow().getQuantity().intValueExact());
        backfill.step(store.getId());
        verify(client, times(2)).getOrdersCreatedBetween(eq(store.getId()), anyLong(), anyLong());
    }
    @Test void unmatchedLinesDoNotPublishPartialCoverageOrEvents() {
        when(client.getOrderLines(eq(store.getId()), anyString())).thenReturn(json.readTree(
                "{\"elements\":[{\"id\":\"good\",\"item\":{\"id\":\"item1\"}},{\"id\":\"bad\",\"item\":{\"id\":\"missing\"}}]}"));
        assertThrows(IllegalStateException.class, () -> backfill.step(store.getId()));
        assertEquals(recent.toInstant(), connection().getSalesCoverageStart().toInstant());
        assertTrue(sales.findWindow(store.getId(), recent.minusDays(46), recent).isEmpty());
        assertNotNull(connection().getSalesBackfillTarget());
    }
    @Test void requestUsesTwoFullCalendarYearsAndCancelKeepsImportedData() {
        backfill.request(store.getId());
        var expected = LocalDate.now(ZoneId.of(store.getTimezone())).withDayOfMonth(1).minusMonths(24)
                .atStartOfDay(ZoneId.of(store.getTimezone())).toInstant();
        assertEquals(expected, connection().getSalesBackfillTarget().toInstant());
        backfill.step(store.getId());
        backfill.cancel(store.getId());
        assertNull(connection().getSalesBackfillTarget());
        assertEquals(recent.minusDays(30).toInstant(), connection().getSalesCoverageStart().toInstant());
        assertEquals(1, sales.findWindow(store.getId(), recent.minusDays(46), recent).size());
    }
    @Test void legacyFractionalBoundaryIsRoundedUpWithoutLosingAMillisecond() {
        var c = connection();
        // New fixture with a pre-millisecond-precision coverage boundary.
        c.completeSalesBackfillStep(recent.minusNanos(500_000)); credentials.saveAndFlush(c);
        backfill.step(store.getId());
        verify(client).getOrdersCreatedBetween(store.getId(), recent.minusDays(30).toInstant().toEpochMilli(), recent.toInstant().toEpochMilli());
    }
}
