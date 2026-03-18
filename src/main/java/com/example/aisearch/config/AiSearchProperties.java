package com.example.aisearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-search")
public record AiSearchProperties(
    /**
     * Elasticsearch 접속 정보
     */
    String elasticsearchUrl,
    String username,
    String password,
    /**
     * 색인 베이스명 (버전 인덱스 prefix)
     */
    String indexName,
    /**
     * 검색 전용 read alias
     */
    String readAlias,
    /**
     * 동의어 세트 ID
     */
    String synonymsSet,
    /**
     * 운영 동의어 파일 경로
     */
    String synonymsFilePath,
    /**
     * 회귀 테스트 동의어 파일 경로
     */
    String synonymsRegressionFilePath,
    /**
     * 임베딩 모델 위치 (DJL 지원 URL)
     */
    String embeddingModelUrl,
    /**
     * 임베딩 모델 로컬 경로 (classpath: 또는 파일 시스템 경로)
     */
    String embeddingModelPath,
    /**
     * 검색 결과 필터링 최소 점수
     */
    double minScoreThreshold,
    /**
     * 카테고리 부스팅 룰 캐시 TTL(초)
     */
    long categoryBoostCacheTtlSeconds,
    /**
     * 검색어 임베딩 캐시 TTL(초)
     */
    long queryEmbeddingCacheTtlSeconds,
    /**
     * 검색어 임베딩 캐시 최대 엔트리 수
     */
    long queryEmbeddingCacheMaxSize,
    /**
     * 검색어 임베딩 생성 제한 시간(ms)
     */
    long queryEmbeddingTimeoutMillis,
    /**
     * 검색어 임베딩 생성용 스레드 수
     */
    int queryEmbeddingExecutorThreads,
    /**
     * 보관할 버전 인덱스 개수(현재 alias 대상 포함)
     */
    int indexRetentionCount
) {
}
