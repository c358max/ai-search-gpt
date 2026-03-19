package com.example.aisearch.service.embedding;

import com.example.aisearch.config.AiSearchProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 검색어 임베딩 생성의 운영 정책을 담당한다.
 *
 * <p>역할:
 * - 검색어 정규화
 * - 동일 검색어 임베딩 캐시
 * - 동일 시점 중복 요청 합류(in-flight dedup)
 * - 시간 제한 초과 시 예외 변환
 */
@Service
public class QueryEmbeddingService {

    private final EmbeddingService embeddingService;
    private final EmbeddingInputFormatter embeddingInputFormatter;
    private final Cache<String, List<Float>> cache;
    private final ConcurrentMap<String, CompletableFuture<List<Float>>> inFlight;
    private final ExecutorService executor;
    private final long timeoutMillis;

    public QueryEmbeddingService(
            EmbeddingService embeddingService,
            EmbeddingInputFormatter embeddingInputFormatter,
            AiSearchProperties properties
    ) {
        this.embeddingService = embeddingService;
        this.embeddingInputFormatter = embeddingInputFormatter;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1L, properties.queryEmbeddingCacheMaxSize()))
                .expireAfterWrite(Duration.ofSeconds(Math.max(1L, properties.queryEmbeddingCacheTtlSeconds())))
                .build();
        this.inFlight = new ConcurrentHashMap<>();
        this.timeoutMillis = Math.max(1L, properties.queryEmbeddingTimeoutMillis());
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, properties.queryEmbeddingExecutorThreads()),
                new QueryEmbeddingThreadFactory()
        );
    }

    public List<Float> toQueryEmbedding(String query) {
        String cacheKey = normalize(query);
        if (cacheKey.isBlank()) {
            throw new IllegalArgumentException("검색어 임베딩을 생성할 query 값이 비어 있습니다.");
        }

        List<Float> cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<List<Float>> future = inFlight.computeIfAbsent(cacheKey, this::startEmbedding);
        try {
            List<Float> embedding = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            cache.put(cacheKey, embedding);
            inFlight.remove(cacheKey, future);
            return embedding;
        } catch (TimeoutException e) {
            future.cancel(true);
            inFlight.remove(cacheKey, future);
            throw new QueryEmbeddingUnavailableException(
                    "검색어 임베딩 생성 시간 초과. timeoutMillis=" + timeoutMillis + ", query=" + cacheKey,
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inFlight.remove(cacheKey, future);
            throw new QueryEmbeddingUnavailableException("검색어 임베딩 생성 대기 중 인터럽트 발생. query=" + cacheKey, e);
        } catch (ExecutionException e) {
            inFlight.remove(cacheKey, future);
            Throwable cause = unwrap(e.getCause());
            if (cause instanceof QueryEmbeddingUnavailableException unavailable) {
                throw unavailable;
            }
            throw new QueryEmbeddingUnavailableException("검색어 임베딩 생성 실패. query=" + cacheKey, cause);
        }
    }

    private CompletableFuture<List<Float>> startEmbedding(String cacheKey) {
        return CompletableFuture.supplyAsync(
                () -> embeddingService.toEmbeddingVector(embeddingInputFormatter.formatQuery(cacheKey)),
                executor
        );
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        return Objects.requireNonNullElseGet(throwable, () -> new IllegalStateException("알 수 없는 임베딩 예외"));
    }

    private String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    private static final class QueryEmbeddingThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "query-embedding-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
