package com.example.aisearch.service.search.categoryboost.store;

import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.search.categoryboost.api.CategoryBoostRules;
import com.example.aisearch.service.search.categoryboost.api.CategoryBoostRulesReloader;
import com.example.aisearch.service.search.categoryboost.store.source.CategoryBoostRuleSnapshot;
import com.example.aisearch.service.search.categoryboost.store.source.CategoryBoostRuleSource;
import com.example.aisearch.service.search.categoryboost.store.source.FileCategoryBoostRuleSource;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * category_boost.json 기반 룰 저장소 구현체.
 * 조회 계약(CategoryBoostRules)과 재로딩 계약(CategoryBoostRulesReloader)을 함께 제공한다.
 *
 * 이 클래스의 역할은 크게 4가지입니다.
 * 1) JSON 파일에서 룰을 읽는다.
 * 2) 메모리에 현재 룰 캐시를 보관한다.
 * 3) 일정 주기(TTL)마다 version 값이 바뀌었는지 확인한다.
 * 4) version 변경이 감지되면 전체 룰을 다시 읽어 캐시를 교체한다.
 *
 * 현재 클래스명은 기존 호환을 위해 유지하고 있지만,
 * 내부적으로는 "source + cache/reload coordinator" 구조로 분리되어 있습니다.
 *
 * - store: 조회/재로딩 진입점
 * - store.source: 룰을 어디서 읽을지 결정하는 협력 객체들
 */
@Component
public class JsonCategoryBoostRules implements CategoryBoostRules, CategoryBoostRulesReloader {

    private static final Logger log = LoggerFactory.getLogger(JsonCategoryBoostRules.class);
    private static final String VERSION_CHECK_GATE_KEY = "version-check-gate";

    private final CategoryBoostRuleSource ruleSource;
    // TTL 동안 "버전 체크를 이미 했다"는 사실만 기록하는 얇은 게이트 캐시다.
    // 실제 룰 데이터는 currentEntry에 들어 있다.
    private final Cache<String, Boolean> versionCheckGate;
    // 현재 메모리에서 사용 중인 룰 스냅샷이다.
    // 조회는 lock 없이 이 값을 읽고, 재로딩 시에만 새 스냅샷으로 통째 교체한다.
    private final AtomicReference<CategoryBoostCacheEntry> currentEntry;

    // 스프링 빈 생성용 기본 생성자:
    // 운영 기본 룰 경로(classpath:data/category_boost.json)와
    // application.yaml의 TTL 설정값(category-boost-cache-ttl-seconds)을 사용한다.
    // 기본 file source와 application.yaml의 TTL 설정값을 사용한다.
    @Autowired
    public JsonCategoryBoostRules(
            CategoryBoostRuleSource ruleSource,
            AiSearchProperties properties
    ) {
        this(ruleSource, properties.categoryBoostCacheTtlSeconds());
    }

    public JsonCategoryBoostRules(
            org.springframework.core.io.ResourceLoader resourceLoader,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            String ruleFilePath,
            long cacheTtlSeconds
    ) {
        this(new FileCategoryBoostRuleSource(resourceLoader, objectMapper, ruleFilePath), cacheTtlSeconds);
    }

    // 테스트/수동 구성용 생성자:
    // 호출자가 file source 경로와 TTL을 직접 지정할 수 있다.
    public JsonCategoryBoostRules(
            CategoryBoostRuleSource ruleSource,
            long cacheTtlSeconds
    ) {
        this.ruleSource = ruleSource;
        // 룰 파일 버전을 매 호출마다 확인하면 I/O 비용이 커지므로 TTL 동안 버전 체크를 생략한다.
        this.versionCheckGate = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofSeconds(Math.max(1L, cacheTtlSeconds)))
                .build();
        this.currentEntry = new AtomicReference<>(CategoryBoostCacheEntry.empty());
        loadInitialRules();
    }

    // 테스트나 운영 제어 코드에서 룰 경로를 교체할 때 사용한다.
    // 경로 변경 직후 다음 조회/재로딩에서 새 파일을 반영하도록 version check gate를 비운다.
    void setRuleFilePath(String ruleFilePath) {
        if (ruleSource instanceof FileCategoryBoostRuleSource fileRuleSource) {
            fileRuleSource.setRuleFilePath(ruleFilePath);
        } else {
            throw new IllegalStateException("rule path switching is only supported for file-based source");
        }
        this.versionCheckGate.invalidate(VERSION_CHECK_GATE_KEY);
    }

    /**
     *
     * @param keyword 정규화된 검색어(trim 이후)
     * @return
     * Map<String, Double>
     *     String : 카테고리 ID
     *      Double : 가중치 (예: 0.2)
     */
    @Override
    public Optional<Map<String, Double>> findByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        // 조회가 자주 들어오더라도 매번 파일을 읽지 않도록,
        // TTL이 만료된 시점에만 version 변경 여부를 확인한다.
        refreshIfNeeded();
        Map<String, Double> boosts = currentEntry.get().rulesByKeyword().get(keyword);
        return boosts == null ? Optional.empty() : Optional.of(boosts);
    }

    @Override
    public void reload() {
        synchronized (this) {
            // 운영 중 수동 reload 요청은 TTL을 기다리지 않고
            // 즉시 version 확인/재로딩을 시도하는 강제 경로다.
            if (checkAndReloadIfVersionChanged()) {
                versionCheckGate.put(VERSION_CHECK_GATE_KEY, Boolean.TRUE);
            }
        }
    }

    private void loadInitialRules() {
        try {
            // 애플리케이션 시작 시점에 첫 룰 스냅샷을 읽어 메모리에 올린다.
            currentEntry.set(toCacheEntry(ruleSource.loadSnapshot()));
            versionCheckGate.put(VERSION_CHECK_GATE_KEY, Boolean.TRUE);
        } catch (Exception e) {
            // 초기 로딩 실패로 서비스 전체가 죽지 않도록 빈 룰로 시작한다.
            log.warn("카테고리 부스팅 초기 룰 로딩 실패. 빈 룰로 동작합니다. source={}", ruleSource.description(), e);
        }
    }

    private void refreshIfNeeded() {
        // 게이트 캐시에 값이 있으면 아직 TTL이 살아 있다는 뜻이므로
        // 이번 조회에서는 version 체크를 생략한다.
        if (versionCheckGate.getIfPresent(VERSION_CHECK_GATE_KEY) != null) {
            return;
        }
        synchronized (this) {
            // 여러 스레드가 동시에 들어와도 한 번만 실제 체크하도록 이중 확인한다.
            if (versionCheckGate.getIfPresent(VERSION_CHECK_GATE_KEY) != null) {
                return;
            }
            if (checkAndReloadIfVersionChanged()) {
                versionCheckGate.put(VERSION_CHECK_GATE_KEY, Boolean.TRUE);
            }
        }
    }

    private boolean checkAndReloadIfVersionChanged() {
        try {
            // 전체 파일을 다 읽기 전에 version만 먼저 확인해
            // 실제 변경이 있을 때만 전체 룰을 다시 적재한다.
            String newVersion = ruleSource.readVersion();
            CategoryBoostCacheEntry cached = currentEntry.get();
            // version 값이 바뀐 경우에만 전체 룰을 다시 읽어 캐시를 교체한다.
            if (!Objects.equals(cached.version(), newVersion)) {
                currentEntry.set(toCacheEntry(ruleSource.loadSnapshot()));
            }
            return true;
        } catch (Exception e) {
            // 재로딩 중 오류가 나도 기존 캐시를 유지해 검색 품질 급락을 방지한다.
            log.warn("카테고리 부스팅 룰 버전 확인/재로딩 실패. 기존 캐시를 유지합니다. source={}", ruleSource.description(), e);
            return false;
        }
    }

    private CategoryBoostCacheEntry toCacheEntry(CategoryBoostRuleSnapshot snapshot) {
        return new CategoryBoostCacheEntry(snapshot.version(), snapshot.rulesByKeyword());
    }

    private record CategoryBoostCacheEntry(
            String version,
            Map<String, Map<String, Double>> rulesByKeyword
    ) {
        private static CategoryBoostCacheEntry empty() {
            // 초기 로딩 실패 시에도 null 대신 빈 스냅샷을 유지해
            // 조회 측 분기와 NPE 가능성을 줄인다.
            return new CategoryBoostCacheEntry("", Map.of());
        }
    }
}
