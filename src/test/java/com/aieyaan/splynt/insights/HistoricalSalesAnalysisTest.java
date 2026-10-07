package com.aieyaan.splynt.insights;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.aieyaan.splynt.product.Product;
class HistoricalSalesAnalysisTest {
    ZoneId zone = ZoneId.of("America/New_York");
    List<SalesEvent> events = new ArrayList<>();
    void month(String month, int orders, int unitsPerDay) {
        YearMonth ym = YearMonth.parse(month);
        for (int i = 0; i < orders; i++) events.add(new SalesEvent(1L, 1L, month + "-" + i, "line",
                BigDecimal.valueOf((long) ym.lengthOfMonth() * unitsPerDay).divide(BigDecimal.valueOf(orders), 8, java.math.RoundingMode.HALF_UP),
                ym.atDay(10).atTime(12, 0).atZone(zone).toOffsetDateTime()));
    }
    HistoricalSalesAnalysis.History analyze(String coverage, String end) {
        Product product = mock(Product.class); when(product.getName()).thenReturn("Coffee");
        return HistoricalSalesAnalysis.calculate(events, Map.of(1L, product), zone, coverage == null ? null : OffsetDateTime.parse(coverage), LocalDate.parse(end));
    }
    @Test void missingCoverageIsUnknownRatherThanZeroSales() {
        var result = analyze(null, "2026-10-07");
        assertEquals(0, result.completeMonths()); assertFalse(result.sufficientForRecurringPatterns());
        assertTrue(result.months().stream().allMatch(m -> m.ordersPerDay() == null && m.orders() == null));
    }
    @Test void partialMonthAndCurrentMonthAreExcluded() {
        month("2026-07", 30, 3); month("2026-08", 30, 4); month("2026-10", 30, 1000);
        var result = analyze("2026-07-01T04:00:01Z", "2026-10-07");
        assertEquals(2, result.completeMonths());
        assertEquals("2026-09", result.months().getLast().month());
        assertNull(result.months().get(21).ordersPerDay());
        assertEquals(0, result.months().getLast().ordersPerDay().signum());
    }
    @Test void leapYearComparisonUsesDailyRatesInsteadOfRawMonthlyTotals() {
        month("2024-02", 58, 10); month("2025-02", 56, 10);
        var result = analyze("2023-01-01T05:00:00Z", "2025-03-01");
        assertEquals(new BigDecimal("0.0"), result.comparisons().getLast().dailyOrdersChangePercent());
        assertEquals(new BigDecimal("0.0"), result.productComparisons().getFirst().dailyUnitsChangePercent());
    }
    @Test void sparseComparisonsDoNotPublishPercentageClaims() {
        month("2024-09", 9, 10); month("2025-09", 30, 20);
        var result = analyze("2023-01-01T05:00:00Z", "2025-10-01");
        assertFalse(result.comparisons().getLast().sufficient());
        assertNull(result.productComparisons().getFirst().dailyUnitsChangePercent());
    }
    @Test void repeatedMonthlyPatternIsNormalizedForAnnualGrowth() {
        YearMonth first = YearMonth.of(2024, 1);
        for (int i = 0; i < 24; i++) { var ym = first.plusMonths(i); month(ym.toString(), ym.lengthOfMonth() * (i < 12 ? 2 : 4) * (ym.getMonthValue() == 12 ? 3 : 1), 10); }
        var result = analyze("2024-01-01T05:00:00Z", "2026-01-01");
        assertTrue(result.sufficientForRecurringPatterns());
        assertEquals(1, result.recurringPatterns().size());
        assertEquals(12, result.recurringPatterns().getFirst().calendarMonth());
        assertEquals("HIGHER", result.recurringPatterns().getFirst().direction());
        assertEquals(new BigDecimal("100.0"), result.comparisons().getLast().dailyOrdersChangePercent());
    }
    @Test void oneYearSpikeIsNotCalledRecurring() {
        YearMonth first = YearMonth.of(2024, 1);
        for (int i = 0; i < 24; i++) month(first.plusMonths(i).toString(), i == 11 ? 300 : 60, 10);
        assertTrue(analyze("2024-01-01T05:00:00Z", "2026-01-01").recurringPatterns().isEmpty());
    }
    @Test void localMidnightDeterminesCalendarMonth() {
        events.add(new SalesEvent(1L, 1L, "order", "line", BigDecimal.TEN, OffsetDateTime.parse("2026-10-01T03:59:59Z")));
        events.add(new SalesEvent(1L, 1L, "today", "line", BigDecimal.TEN, OffsetDateTime.parse("2026-10-01T04:00:00Z")));
        var result = analyze("2026-09-01T04:00:00Z", "2026-10-01");
        assertEquals(new BigDecimal("0.033"), result.months().getLast().ordersPerDay());
        assertEquals(1, result.months().getLast().orders());
    }
    @Test void unlikeQuantitiesAndMultipleLinesCannotInflateStorePatterns() {
        month("2025-09", 30, 10); month("2026-09", 30, 100000);
        events.add(new SalesEvent(1L, 2L, "2026-09-0", "extra-weighted-line", new BigDecimal("999999"),
                OffsetDateTime.parse("2026-09-10T16:00:00Z")));
        var result = analyze("2024-01-01T05:00:00Z", "2026-10-01");
        assertEquals("ORDERS", result.metric());
        assertEquals(30, result.months().getLast().orders());
        assertEquals(new BigDecimal("1.000"), result.months().getLast().ordersPerDay());
        assertEquals(new BigDecimal("0.0"), result.comparisons().getLast().dailyOrdersChangePercent());
        assertTrue(result.productComparisons().getFirst().dailyUnitsChangePercent().signum() > 0);
    }

}
