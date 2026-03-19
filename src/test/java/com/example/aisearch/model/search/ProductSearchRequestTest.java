package com.example.aisearch.model.search;

import com.example.aisearch.support.SearchDebugPrintSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductSearchRequestTest {

    @Test
    @DisplayName("검색어 양끝 공백을 제거하고 기본 정렬을 연관도순으로 설정한다")
    void shouldTrimQueryAndDefaultSortOption() {
        ProductSearchRequest request = new ProductSearchRequest("  간식  ", null, null, null);
        SearchDebugPrintSupport.printRequest("trimmed-default", request);

        assertEquals("간식", request.query());
        assertEquals(SearchSortOption.RELEVANCE_DESC, request.sortOption());
        assertTrue(request.hasQuery());
    }

    @Test
    @DisplayName("명시한 정렬 옵션을 그대로 사용한다")
    void shouldUseExplicitSortOption() {
        ProductSearchRequest request = new ProductSearchRequest("간식", null, null, SearchSortOption.PRICE_ASC);
        assertEquals(SearchSortOption.PRICE_ASC, request.sortOption());
    }

    @Test
    @DisplayName("카테고리 부스팅 정렬 옵션을 그대로 사용한다")
    void shouldUseCategoryBoostingSortOption() {
        ProductSearchRequest request = new ProductSearchRequest("사과", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        assertEquals(SearchSortOption.CATEGORY_BOOSTING_DESC, request.sortOption());
    }

    @Test
    @DisplayName("검색어가 비어 있으면 카테고리 부스팅 정렬을 연관도순으로 되돌린다")
    void shouldFallbackCategoryBoostingToRelevanceWhenQueryIsBlank() {
        ProductSearchRequest request = new ProductSearchRequest("   ", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        assertEquals(SearchSortOption.RELEVANCE_DESC, request.sortOption());
    }

    @Test
    @DisplayName("빈 검색어는 선택 조건으로 처리하고 카테고리 목록은 중복 제거한다")
    void shouldTreatBlankQueryAsOptional() {
        ProductSearchRequest request = new ProductSearchRequest("   ", null, List.of(1, 2, 2), null);

        assertNull(request.query());
        assertFalse(request.hasQuery());
        assertEquals(List.of(1, 2), request.categoryIds());
        assertTrue(request.hasCategoryCondition());
    }

    @Test
    @DisplayName("최소 가격이 최대 가격보다 크면 예외를 던진다")
    void shouldRejectInvalidPriceRange() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new SearchPrice(10000, 1000)
        );
        assertTrue(ex.getMessage().contains("minPrice"));
    }

    @Test
    @DisplayName("페이지 번호가 1보다 작으면 예외를 던진다")
    void shouldRejectInvalidPageSizePolicy() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SearchPagingPolicy.toPageable(0, 5)
        );
        assertTrue(ex.getMessage().contains("page"));
    }
}
