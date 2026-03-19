package com.example.aisearch.service.search.categoryboost.policy;

import com.example.aisearch.model.search.ProductSearchRequest;
import com.example.aisearch.model.search.SearchSortOption;
import com.example.aisearch.service.search.categoryboost.api.CategoryBoostRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CategoryBoostingDeciderTest {

    @Test
    @DisplayName("카테고리 부스팅 정렬이 아니면 기존 정렬 옵션을 유지한다")
    void shouldKeepOriginalSortWhenSortOptionIsNotCategoryBoosting() {
        CategoryBoostRules rules = keyword -> Optional.of(Map.of("1", 0.2));
        CategoryBoostingDecider decider = new CategoryBoostingDecider(rules);

        ProductSearchRequest request = new ProductSearchRequest("간식", null, null, SearchSortOption.PRICE_ASC);
        CategoryBoostingResult result = decider.decide(request);

        assertFalse(result.applyCategoryBoost());
        assertEquals(SearchSortOption.PRICE_ASC, result.searchSortOption());
    }

    @Test
    @DisplayName("키워드 룰이 일치하면 카테고리 부스팅을 적용한다")
    void shouldApplyBoostWhenKeywordMatches() {
        CategoryBoostRules rules = keyword -> "간식".equals(keyword)
                ? Optional.of(Map.of("1", 0.2))
                : Optional.empty();
        CategoryBoostingDecider decider = new CategoryBoostingDecider(rules);

        ProductSearchRequest request = new ProductSearchRequest("  간식  ", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        CategoryBoostingResult result = decider.decide(request);

        assertTrue(result.applyCategoryBoost());
        assertEquals(SearchSortOption.CATEGORY_BOOSTING_DESC, result.searchSortOption());
        assertEquals(0.2, result.categoryBoostById().get("1"));
    }

    @Test
    @DisplayName("키워드 룰이 없으면 카테고리 부스팅 정렬을 연관도순으로 되돌린다")
    void shouldFallbackToRelevanceWhenKeywordDoesNotMatch() {
        CategoryBoostRules rules = keyword -> Optional.empty();
        CategoryBoostingDecider decider = new CategoryBoostingDecider(rules);

        ProductSearchRequest request = new ProductSearchRequest("오징어 튀김", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        CategoryBoostingResult result = decider.decide(request);

        assertFalse(result.applyCategoryBoost());
        assertEquals(SearchSortOption.RELEVANCE_DESC, result.searchSortOption());
    }
}
