package com.aieyaan.splynt.advice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.aieyaan.splynt.insights.*;
import com.aieyaan.splynt.tenant.*;

/** Separate committed transactions on separate threads, including concurrent first-report creation. */
@SpringBootTest
class AdviceConcurrencyTest {
    @Autowired AdviceService advice;
    @Autowired AdviceReportRepository reports;
    @Autowired StoreRepository stores;
    @Autowired OrganizationRepository organizations;
    @MockitoBean OpenAiAdviceClient client;
    @MockitoBean InsightsService insights;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void simultaneousRequestsOnlyCallProviderOnceEvenOnFailure(boolean providerFails) throws Exception {
        String slug = "concurrent-" + UUID.randomUUID();
        var org = organizations.saveAndFlush(new Organization("Shop", slug));
        var store = stores.saveAndFlush(new Store(org, "Shop", slug));
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("test-model");
        var source = new InsightsService.Insights("Shop", "Chicago, US", "America/Chicago", OffsetDateTime.now(), null,
                LocalDate.now().minusDays(30), LocalDate.now(), new InsightsService.Summary(30, 35, BigDecimal.valueOf(70), true,
                List.of(new InsightsService.ProductInsight(1L, "Coffee", BigDecimal.valueOf(70), BigDecimal.valueOf(2), BigDecimal.TEN, BigDecimal.valueOf(5))),
                Collections.nCopies(24, BigDecimal.ZERO), Collections.nCopies(7, BigDecimal.ZERO)), "Test timing", "Test history",
                HistoricalSalesAnalysis.calculate(List.of(), Map.of(), ZoneId.of("America/Chicago"), null, LocalDate.now()));
        when(insights.read(store.getId())).thenReturn(source);
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        when(client.generate(anyMap())).thenAnswer(call -> {
            providerEntered.countDown();
            if (!releaseProvider.await(10, TimeUnit.SECONDS)) throw new AssertionError("Provider release timed out");
            if (providerFails) throw new IllegalStateException("Private provider failure");
            return OpenAiAdviceClientTest.content();
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdviceService.View> first = executor.submit(() -> advice.generate(store.getId()));
            assertTrue(providerEntered.await(10, TimeUnit.SECONDS), "First request must reach provider");
            Future<AdviceService.View> second = executor.submit(() -> {
                secondStarted.countDown();
                return advice.generate(store.getId());
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS), "Second transaction must wait for store lock");
            releaseProvider.countDown();
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertFalse(firstResult.canGenerate()); assertFalse(secondResult.canGenerate());
            // Linux clocks can supply nanoseconds; database timestamps persist microseconds.
            assertTrue(Duration.between(firstResult.nextGenerationAt(), secondResult.nextGenerationAt()).abs().toNanos() <= 1_000,
                    "Both requests must observe the same cooldown, allowing database timestamp rounding");
            if (providerFails) {
                assertNull(firstResult.report()); assertNull(secondResult.report());
                assertNotNull(secondResult.error()); assertFalse(secondResult.error().contains("Private"));
            } else {
                assertNotNull(firstResult.report());
                assertEquals(firstResult.report(), secondResult.report());
            }
            verify(client, times(1)).generate(anyMap());
            assertTrue(reports.findById(store.getId()).isPresent());
            assertFalse(advice.generate(store.getId()).canGenerate());
            verify(client, times(1)).generate(anyMap());
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
