package com.aieyaan.splynt.insights;

import java.math.*;
import java.time.*;
import java.util.*;
import com.aieyaan.splynt.product.Product;

/** Descriptive comparisons; no claim that calendar month caused a change in sales. */
public final class HistoricalSalesAnalysis {
    private HistoricalSalesAnalysis() {}
    public static History calculate(List<SalesEvent> events, Map<Long, Product> catalog, ZoneId zone,
            OffsetDateTime coverage, LocalDate end) {
        YearMonth until = YearMonth.from(end);
        YearMonth first = until.minusMonths(24);
        LocalDate firstDay = coverage == null ? end : coverage.atZoneSameInstant(zone).toLocalDate();
        if (coverage != null && !coverage.toInstant().equals(firstDay.atStartOfDay(zone).toInstant())) firstDay = firstDay.plusDays(1);
        Map<YearMonth, Bucket> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 24; i++) buckets.put(first.plusMonths(i), new Bucket());
        for (SalesEvent event : events) {
            YearMonth month = YearMonth.from(event.getOccurredAt().atZoneSameInstant(zone));
            Bucket bucket = buckets.get(month);
            if (bucket == null) continue;
            bucket.orders.add(event.getOrderId());
            bucket.quantityUnits.computeIfAbsent(event.getProductId(), ignored -> new HashSet<>()).add(Objects.toString(event.getQuantityUnit(), "unknown"));
            bucket.products.merge(event.getProductId(), event.getUnits(), BigDecimal::add);
            bucket.productOrders.computeIfAbsent(event.getProductId(), ignored -> new HashSet<>()).add(event.getOrderId());
        }
        List<Month> months = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            var month = entry.getKey(); var bucket = entry.getValue();
            boolean complete = coverage != null && !month.atDay(1).isBefore(firstDay) && !month.plusMonths(1).atDay(1).isAfter(end);
            months.add(new Month(month.toString(), complete, month.lengthOfMonth(), complete ? bucket.orders.size() : null,
                    complete ? daily(BigDecimal.valueOf(bucket.orders.size()), month.lengthOfMonth()) : null));
        }
        List<Comparison> comparisons = new ArrayList<>();
        for (int i = 12; i < 24; i++) {
            Month recent = months.get(i), previous = months.get(i - 12);
            boolean sufficient = recent.complete() && previous.complete() && recent.orders() >= 30 && previous.orders() >= 30;
            comparisons.add(new Comparison(recent.month(), previous.month(), sufficient,
                    sufficient ? change(BigDecimal.valueOf(recent.orders()), recent.days(), BigDecimal.valueOf(previous.orders()), previous.days()) : null));
        }
        boolean twoCycles = months.stream().allMatch(m -> m.complete() && m.orders() >= 30);
        List<Pattern> patterns = new ArrayList<>();
        if (twoCycles) {
            BigDecimal[] baseline = new BigDecimal[2];
            for (int cycle = 0; cycle < 2; cycle++) {
                BigDecimal total = BigDecimal.ZERO; int days = 0;
                for (int i = cycle * 12; i < (cycle + 1) * 12; i++) { total = total.add(BigDecimal.valueOf(months.get(i).orders())); days += months.get(i).days(); }
                baseline[cycle] = total.divide(BigDecimal.valueOf(days), 12, RoundingMode.HALF_UP);
            }
            for (int i = 0; i < 12; i++) {
                Month earlier = months.get(i), later = months.get(i + 12);
                BigDecimal a = index(earlier, baseline[0]), b = index(later, baseline[1]);
                if (a == null || b == null) continue;
                String direction = a.compareTo(new BigDecimal("1.20")) >= 0 && b.compareTo(new BigDecimal("1.20")) >= 0 ? "HIGHER"
                        : a.compareTo(new BigDecimal("0.80")) <= 0 && b.compareTo(new BigDecimal("0.80")) <= 0 ? "LOWER" : null;
                if (direction != null) patterns.add(new Pattern(YearMonth.parse(later.month()).getMonthValue(), direction,
                        a.setScale(2, RoundingMode.HALF_UP), b.setScale(2, RoundingMode.HALF_UP)));
            }
        }
        List<ProductComparison> productComparisons = new ArrayList<>();
        Month recent = months.getLast(), previous = months.get(11);
        if (recent.complete() && previous.complete()) {
            Bucket a = buckets.get(until.minusMonths(1)), b = buckets.get(until.minusMonths(13));
            a.products.entrySet().stream().filter(e -> catalog.containsKey(e.getKey()))
                    .sorted(Comparator.<Map.Entry<Long, BigDecimal>>comparingInt(e -> a.productOrders.get(e.getKey()).size()).reversed().thenComparing(Map.Entry::getKey)).limit(10).forEach(entry -> {
                        Long id = entry.getKey(); BigDecimal prior = b.products.getOrDefault(id, BigDecimal.ZERO);
                        int recentOrders = a.productOrders.getOrDefault(id, Set.of()).size(), priorOrders = b.productOrders.getOrDefault(id, Set.of()).size();
                        Set<String> observedUnits = new HashSet<>(a.quantityUnits.getOrDefault(id, Set.of()));
                        observedUnits.addAll(b.quantityUnits.getOrDefault(id, Set.of()));
                        boolean comparable = observedUnits.size() == 1 && !observedUnits.contains("unknown");
                        String unit = comparable ? observedUnits.iterator().next() : "Mixed or unknown units";
                        boolean enough = comparable && recentOrders >= 10 && priorOrders >= 10;
                        productComparisons.add(new ProductComparison(id, catalog.get(id).getName(), recent.month(), previous.month(),
                                comparable ? entry.getValue() : null, comparable ? prior : null, recentOrders, priorOrders, enough,
                                enough ? change(entry.getValue(), recent.days(), prior, previous.days()) : null, unit));
                    });
        }
        String status = !twoCycles
                ? "Recurring calendar-month patterns need 24 fully covered months with at least 30 included orders in each month. Available month comparisons are descriptive only."
                : patterns.isEmpty() ? "Two full annual cycles are available, but no month repeats a higher/lower pattern under the stated rule."
                : "Some calendar months repeat a higher/lower pattern across two annual cycles. This is exploratory evidence, not proof of seasonal demand or a forecast.";
        return new History((int) months.stream().filter(Month::complete).count(), twoCycles, months, comparisons, patterns, productComparisons, status,
                "Completed calendar months in the store timezone only. Missing coverage is unknown, not zero sales. Store-wide year-over-year changes compare distinct included orders per calendar day, accounting for month length and leap years. Recurring patterns require both monthly daily rates to be at least 20% above or below their respective 12-month daily averages. Product comparisons retain each product’s quantity unit and need 10 included orders per product in both periods. These are sample-size heuristics, not statistical significance tests; promotions, stockouts, assortment changes and store closures are not controlled.", "ORDERS");
    }
    private static BigDecimal daily(BigDecimal units, int days) { return units.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP); }
    private static BigDecimal change(BigDecimal current, int currentDays, BigDecimal prior, int priorDays) {
        if (prior.signum() == 0) return null;
        return current.multiply(BigDecimal.valueOf(priorDays)).divide(prior.multiply(BigDecimal.valueOf(currentDays)), 10, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }
    private static BigDecimal index(Month month, BigDecimal baseline) {
        return baseline.signum() == 0 ? null : BigDecimal.valueOf(month.orders()).divide(BigDecimal.valueOf(month.days()), 12, RoundingMode.HALF_UP).divide(baseline, 8, RoundingMode.HALF_UP);
    }
    private static class Bucket {
        Set<String> orders = new HashSet<>();
        Map<Long, Set<String>> quantityUnits = new HashMap<>();
        Map<Long, BigDecimal> products = new HashMap<>();
        Map<Long, Set<String>> productOrders = new HashMap<>();
    }
    public record Month(String month, boolean complete, int days, Integer orders, BigDecimal ordersPerDay) {}
    public record Comparison(String month, String previousMonth, boolean sufficient, BigDecimal dailyOrdersChangePercent) {}
    public record Pattern(int calendarMonth, String direction, BigDecimal earlierIndex, BigDecimal latestIndex) {}
    public record ProductComparison(Long productId, String name, String month, String previousMonth, BigDecimal units, BigDecimal previousUnits,
            int orders, int previousOrders, boolean sufficient, BigDecimal dailyUnitsChangePercent, String unit) {}
    public record History(int completeMonths, boolean sufficientForRecurringPatterns, List<Month> months, List<Comparison> comparisons,
            List<Pattern> recurringPatterns, List<ProductComparison> productComparisons, String status, String methodology, String metric) {}
}
