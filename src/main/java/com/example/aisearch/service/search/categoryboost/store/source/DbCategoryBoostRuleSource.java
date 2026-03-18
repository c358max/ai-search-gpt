package com.example.aisearch.service.search.categoryboost.store.source;

import java.io.IOException;

/**
 * 향후 DB 기반 카테고리 부스팅 룰 저장소를 위한 자리입니다.
 *
 * 아직 실제 연결/조회 구현은 하지 않았고,
 * source abstraction에 맞는 클래스 구조만 먼저 정의합니다.
 */
public class DbCategoryBoostRuleSource implements CategoryBoostRuleSource {

    @Override
    public String readVersion() throws IOException {
        throw new UnsupportedOperationException("DB source is not implemented yet");
    }

    @Override
    public CategoryBoostRuleSnapshot loadSnapshot() throws IOException {
        throw new UnsupportedOperationException("DB source is not implemented yet");
    }

    @Override
    public String description() {
        return "db-category-boost-rule-source";
    }
}
