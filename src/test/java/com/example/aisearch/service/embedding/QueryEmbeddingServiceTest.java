package com.example.aisearch.service.embedding;

import com.example.aisearch.config.AiSearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryEmbeddingServiceTest {

    @Test
    void shouldReuseCachedEmbeddingForNormalizedQuery() {
        CountingEmbeddingService embeddingService = new CountingEmbeddingService(0L);
        QueryEmbeddingService queryEmbeddingService = new QueryEmbeddingService(
                embeddingService,
                new EmbeddingInputFormatter(testProperties(1000L, 100L)),
                testProperties(1000L, 100L)
        );
        try {
            List<Float> first = queryEmbeddingService.toQueryEmbedding("  어린이   간식 ");
            List<Float> second = queryEmbeddingService.toQueryEmbedding("어린이 간식");

            assertSame(first, second);
            assertEquals(1, embeddingService.invocationCount());
        } finally {
            queryEmbeddingService.close();
        }
    }

    @Test
    void shouldDeduplicateConcurrentEmbeddingRequests() throws Exception {
        CountingEmbeddingService embeddingService = new CountingEmbeddingService(200L);
        QueryEmbeddingService queryEmbeddingService = new QueryEmbeddingService(
                embeddingService,
                new EmbeddingInputFormatter(testProperties(1000L, 100L)),
                testProperties(1000L, 100L)
        );
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch startLatch = new CountDownLatch(1);

            Future<List<Float>> first = executor.submit(() -> {
                startLatch.await(1, TimeUnit.SECONDS);
                return queryEmbeddingService.toQueryEmbedding("단백질 간편식");
            });
            Future<List<Float>> second = executor.submit(() -> {
                startLatch.await(1, TimeUnit.SECONDS);
                return queryEmbeddingService.toQueryEmbedding("단백질  간편식");
            });

            startLatch.countDown();

            List<Float> firstResult = first.get(2, TimeUnit.SECONDS);
            List<Float> secondResult = second.get(2, TimeUnit.SECONDS);

            assertSame(firstResult, secondResult);
            assertEquals(1, embeddingService.invocationCount());
        } finally {
            queryEmbeddingService.close();
        }
    }

    @Test
    void shouldThrowWhenEmbeddingGenerationTimesOut() {
        CountingEmbeddingService embeddingService = new CountingEmbeddingService(200L);
        QueryEmbeddingService queryEmbeddingService = new QueryEmbeddingService(
                embeddingService,
                new EmbeddingInputFormatter(testProperties(50L, 100L)),
                testProperties(50L, 100L)
        );
        try {
            assertThrows(
                    QueryEmbeddingUnavailableException.class,
                    () -> queryEmbeddingService.toQueryEmbedding("느린 검색어")
            );
        } finally {
            queryEmbeddingService.close();
        }
    }

    private AiSearchProperties testProperties(long timeoutMillis, long cacheTtlSeconds) {
        return new AiSearchProperties(
                "http://localhost:9200",
                "elastic",
                "password",
                "food-products",
                "food-products-read",
                "food-synonyms",
                "classpath:es/dictionary/synonyms_ko.txt",
                "classpath:es/dictionary/synonyms_kr_regression.txt",
                "djl://test",
                "classpath:/model/test",
                0.71,
                300L,
                cacheTtlSeconds,
                100L,
                timeoutMillis,
                2,
                3
        );
    }

    private static final class CountingEmbeddingService implements EmbeddingService {

        private final long delayMillis;
        private final AtomicInteger invocationCount = new AtomicInteger();

        private CountingEmbeddingService(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public List<Float> toEmbeddingVector(String text) {
            invocationCount.incrementAndGet();
            if (delayMillis > 0L) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                }
            }
            return List.of((float) text.length());
        }

        @Override
        public int dimensions() {
            return 1;
        }

        private int invocationCount() {
            return invocationCount.get();
        }
    }
}
