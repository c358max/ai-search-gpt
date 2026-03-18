package com.example.aisearch.model.search;

import com.example.aisearch.support.SearchDebugPrintSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductSearchRequestTest {

    @Test
    void shouldTrimQueryAndDefaultSortOption() {
        ProductSearchRequest request = new ProductSearchRequest("  간식  ", null, null, null);
        SearchDebugPrintSupport.printRequest("trimmed-default", request);

        assertEquals("간식", request.query());
        assertEquals(SearchSortOption.RELEVANCE_DESC, request.sortOption());
        assertTrue(request.hasQuery());
    }

    @Test
    void shouldUseExplicitSortOption() {
        ProductSearchRequest request = new ProductSearchRequest("간식", null, null, SearchSortOption.PRICE_ASC);
        assertEquals(SearchSortOption.PRICE_ASC, request.sortOption());
    }

    @Test
    void shouldUseCategoryBoostingSortOption() {
        ProductSearchRequest request = new ProductSearchRequest("사과", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        assertEquals(SearchSortOption.CATEGORY_BOOSTING_DESC, request.sortOption());
    }

    @Test
    void shouldFallbackCategoryBoostingToRelevanceWhenQueryIsBlank() {
        ProductSearchRequest request = new ProductSearchRequest("   ", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        assertEquals(SearchSortOption.RELEVANCE_DESC, request.sortOption());
    }

    @Test
    void shouldTreatBlankQueryAsOptional() {
        ProductSearchRequest request = new ProductSearchRequest("   ", null, List.of(1, 2, 2), null);

        assertNull(request.query());
        assertFalse(request.hasQuery());
        assertEquals(List.of(1, 2), request.categoryIds());
        assertTrue(request.hasCategoryCondition());
    }

    @Test
    void shouldRejectInvalidPriceRange() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new SearchPrice(10000, 1000)
        );
        assertTrue(ex.getMessage().contains("minPrice"));
    }

    @Test
    void shouldRejectInvalidPageSizePolicy() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SearchPagingPolicy.toPageable(0, 5)
        );
        assertTrue(ex.getMessage().contains("page"));
    }
}
