package com.aieyaan.splynt.insights;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.aieyaan.splynt.product.Product;

class InsightsServiceTest {
    private final ZoneId zone = ZoneId.of("America/New_York");
    @Test void stoppedImporterDoesNotCountMissingDaysAsZeroSales() {
        var window = InsightsService.analysisWindow(OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-10-01T02:00:00Z"), zone, Instant.parse("2026-10-07T12:00:00Z"));
        assertEquals(LocalDate.of(2026, 9, 30), window.end());
        assertEquals(LocalDate.of(2026, 8, 31), window.start());
    }
    @Test void partialInitialDayIsExcludedButExactMidnightIsIncluded() {
        var synced = OffsetDateTime.parse("2026-10-07T12:00:00Z");
        var now = synced.toInstant();
        assertEquals(LocalDate.of(2026, 10, 2), InsightsService.analysisWindow(
                OffsetDateTime.parse("2026-10-01T04:00:01Z"), synced, zone, now).start());
        assertEquals(LocalDate.of(2026, 10, 1), InsightsService.analysisWindow(
                OffsetDateTime.parse("2026-10-01T04:00:00Z"), synced, zone, now).start());
    }
    @Test void noSuccessfulImportHasNoAnalyzableDays() {
        var window = InsightsService.analysisWindow(OffsetDateTime.now(), null, zone, Instant.now());
        assertEquals(window.start(), window.end());
    }
    @Test void timezoneBoundariesExcludeTodayAndGroupByLocalOrderTime() {
        var events = List.of(event("a", "2026-10-01T03:59:59Z", "2"),
                event("b", "2026-10-01T04:00:00Z", "3"),
                event("c", "2026-10-02T03:59:59Z", "4"),
                event("d", "2026-10-02T04:00:00Z", "100"));
        var summary = InsightsService.calculate(events, Map.of(), zone,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        assertEquals(2, summary.orders());
        assertEquals(new BigDecimal("7"), summary.units());
        assertEquals(new BigDecimal("3"), summary.hourlyUnits().get(0));
        assertEquals(new BigDecimal("4"), summary.hourlyUnits().get(23));
        assertEquals(new BigDecimal("7"), summary.weekdayUnits().get(3));
        assertFalse(summary.sufficientForVelocity());
    }
    @Test void velocityIncludesZeroSaleDaysAndCountsOrdersOnce() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L); when(product.getName()).thenReturn("Coffee");
        when(product.getQuantity()).thenReturn(BigDecimal.valueOf(12));
        when(product.isStockKnown()).thenReturn(true);
        List<SalesEvent> events = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            events.add(event("order" + i, "2026-09-02T14:00:00Z", "1"));
            events.add(event("order" + i, "2026-09-02T14:00:00Z", "1"));
        }
        var summary = InsightsService.calculate(events, Map.of(1L, product), zone,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        assertEquals(30, summary.orders());
        assertEquals(30, summary.completeDays());
        assertTrue(summary.sufficientForVelocity());
        assertEquals(new BigDecimal("2.000"), summary.topProducts().getFirst().unitsPerDay());
        assertEquals(new BigDecimal("6.0"), summary.topProducts().getFirst().estimatedDaysRemaining());
    }
    @Test void daylightSavingUsesCalendarDaysRatherThanElapsedHours() {
        var window = InsightsService.analysisWindow(OffsetDateTime.parse("2026-03-07T05:00:00Z"),
                OffsetDateTime.parse("2026-03-09T04:00:00Z"), zone, Instant.parse("2026-03-09T12:00:00Z"));
        var summary = InsightsService.calculate(List.of(), Map.of(), zone, window.start(), window.end());
        assertEquals(2, summary.completeDays());
    }
    @Test void timingAndLeadersUseOrdersRatherThanAddingUnlikeQuantities() {
        Product weighted = mock(Product.class), counted = mock(Product.class);
        when(weighted.getId()).thenReturn(1L); when(weighted.getName()).thenReturn("Bulk spice");
        when(weighted.getCloverDetails()).thenReturn(new com.aieyaan.splynt.product.CloverCatalogDetails(null, null, "oz", "PER_UNIT", true, false, List.of()));
        when(counted.getId()).thenReturn(2L); when(counted.getName()).thenReturn("Bottles");
        var at = OffsetDateTime.parse("2026-09-02T14:00:00Z");
        var events = List.of(new SalesEvent(1L, 1L, "one", "a", new BigDecimal("1000"), at, "oz"),
                new SalesEvent(1L, 1L, "one", "b", new BigDecimal("2000"), at, "oz"),
                new SalesEvent(1L, 2L, "one", "c", BigDecimal.ONE, at),
                new SalesEvent(1L, 2L, "two", "d", BigDecimal.ONE, at));
        var result = InsightsService.calculate(events, Map.of(1L, weighted, 2L, counted), zone,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        assertEquals(new BigDecimal("2"), result.hourlyOrders().get(10));
        assertEquals(new BigDecimal("2"), result.weekdayOrders().get(2));
        assertEquals(2L, result.topProducts().getFirst().productId());
        assertEquals(2, result.topProducts().getFirst().orders());
        assertEquals("oz", result.topProducts().get(1).unit());
        assertEquals(new BigDecimal("3000"), result.topProducts().get(1).units());
    }
    @Test void changedMeasurementSuppressesQuantityRatesButKeepsOrderCounts() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L); when(product.getName()).thenReturn("Spice");
        when(product.isStockKnown()).thenReturn(true); when(product.getQuantity()).thenReturn(BigDecimal.TEN);
        when(product.getCloverDetails()).thenReturn(new com.aieyaan.splynt.product.CloverCatalogDetails(null, null, "lb", "PER_UNIT", true, false, List.of()));
        List<SalesEvent> events = new ArrayList<>();
        for (int i = 0; i < 30; i++) events.add(new SalesEvent(1L, 1L, "order-" + i, "line", BigDecimal.ONE,
                OffsetDateTime.parse("2026-09-10T16:00:00Z"), "oz"));
        var result = InsightsService.calculate(events, Map.of(1L, product), zone, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        assertNotNull(result.topProducts().getFirst().unitsPerDay());
        assertNull(result.topProducts().getFirst().estimatedDaysRemaining());
        assertNull(result.topProducts().getFirst().currentStock());
        events.add(new SalesEvent(1L, 1L, "new-unit", "line", BigDecimal.ONE, OffsetDateTime.parse("2026-09-11T16:00:00Z"), "lb"));
        result = InsightsService.calculate(events, Map.of(1L, product), zone, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        assertEquals(31, result.orders()); assertEquals(31, result.topProducts().getFirst().orders());
        assertNull(result.topProducts().getFirst().units()); assertNull(result.topProducts().getFirst().unitsPerDay());
    }
    private SalesEvent event(String order, String at, String quantity) {
        return new SalesEvent(1L, 1L, order, UUID.randomUUID().toString(), new BigDecimal(quantity), OffsetDateTime.parse(at));
    }
}
