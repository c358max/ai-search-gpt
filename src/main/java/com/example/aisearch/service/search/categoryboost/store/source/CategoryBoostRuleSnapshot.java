package com.example.aisearch.service.search.categoryboost.store.source;

import java.util.Map;

/**
 * 특정 시점의 카테고리 부스팅 룰 스냅샷입니다.
 *
 * version:
 * - 원천 데이터의 변경 여부를 판별하기 위한 버전 값
 *
 * rulesByKeyword:
 * - 검색어 -> 카테고리 ID별 부스팅 점수 맵
 */
public record CategoryBoostRuleSnapshot(
        String version,
        Map<String, Map<String, Double>> rulesByKeyword
) {
}
