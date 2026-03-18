package com.example.aisearch;

import com.example.aisearch.model.SearchHitResult;
import com.example.aisearch.model.search.ProductSearchRequest;
import com.example.aisearch.model.search.SearchSortOption;
import com.example.aisearch.service.indexing.orchestration.IndexRolloutService;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostBetaTuner;
import com.example.aisearch.service.search.ProductSearchService;
import com.example.aisearch.support.RequiresElasticsearch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

import java.util.List;
@SpringBootTest(properties = {
        "ai-search.index-name=categoryboost-it-products",
        "ai-search.read-alias=categoryboost-it-products-read",
        "ai-search.synonyms-set=categoryboost-it-synonyms"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryBoostingTest extends ElasticsearchIntegrationTestBase {

    @Autowired
    private IndexRolloutService indexRolloutService;

    @Autowired
    private ProductSearchService productSearchService;

    @Autowired
    private CategoryBoostBetaTuner categoryBoostBetaTuner;

    @BeforeAll
    void setUp() throws Exception {
        printIsolationConfig("CategoryBoostingTest");
        deleteAllVersionedIndices();
        var rollout = indexRolloutService.rollOutFromSourceData();
        Assertions.assertTrue(rollout.indexedCount() >= 100, "최소 100건 이상 인덱싱되어야 합니다.");
    }

    @Test
    void categoryBoostingSortShouldBoostFruitCategoryForAppleKeyword() {
        ProductSearchRequest request = new ProductSearchRequest("사과", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        List<SearchHitResult> results = productSearchService.searchPage(request, pageRequest(1, 5)).results();

        Assertions.assertFalse(results.isEmpty(), "카테고리 부스팅 검증을 위한 결과가 필요합니다.");
        long fruitCount = countCategoryInTopN(results, 5, 4);
        int topN = Math.min(5, results.size());
        long expectedMin = Math.min(2, topN);
        Assertions.assertTrue(fruitCount >= expectedMin,
                "상위 " + topN + "개 중 categoryId=4(과일) 문서가 최소 " + expectedMin + "개 이상이어야 합니다. actual=" + fruitCount);
    }

    @Test
    void categoryBoostingSortShouldFallbackToRelevanceWhenAppleJamKeywordDoesNotMatch() {
        Pageable pageable = pageRequest(1, 10);
        ProductSearchRequest categoryBoostSortRequest = new ProductSearchRequest("사과잼", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        ProductSearchRequest relevanceSortRequest = new ProductSearchRequest("사과잼", null, null, SearchSortOption.RELEVANCE_DESC);

        List<SearchHitResult> boostedResults = productSearchService.searchPage(categoryBoostSortRequest, pageable).results();
        List<SearchHitResult> relevanceResults = productSearchService.searchPage(relevanceSortRequest, pageable).results();

        Assertions.assertEquals(SearchResultTestSupport.extractIds(relevanceResults), SearchResultTestSupport.extractIds(boostedResults),
                "키워드 불일치(사과잼) 시 CATEGORY_BOOSTING_DESC는 RELEVANCE_DESC와 동일 순서여야 합니다.");
    }

    @Test
    void categoryBoostingSortShouldFallbackToRelevanceWhenQueryIsBlank() {
        Pageable pageable = pageRequest(1, 10);
        ProductSearchRequest categoryBoostSortRequest = new ProductSearchRequest("   ", null, List.of(1, 2, 7), SearchSortOption.CATEGORY_BOOSTING_DESC);
        ProductSearchRequest relevanceSortRequest = new ProductSearchRequest("   ", null, List.of(1, 2, 7), SearchSortOption.RELEVANCE_DESC);

        List<SearchHitResult> boostedResults = productSearchService.searchPage(categoryBoostSortRequest, pageable).results();
        List<SearchHitResult> relevanceResults = productSearchService.searchPage(relevanceSortRequest, pageable).results();

        Assertions.assertEquals(SearchResultTestSupport.extractIds(relevanceResults), SearchResultTestSupport.extractIds(boostedResults),
                "q가 blank면 CATEGORY_BOOSTING_DESC 요청도 RELEVANCE_DESC와 동일 동작이어야 합니다.");
    }

    @Test
    void categoryBoostingShouldHaveNoEffectWhenBetaIsZero() {
        categoryBoostBetaTuner.setBeta(0.0);

        Pageable pageable = pageRequest(1, 10);
        ProductSearchRequest categoryBoostSortRequest = new ProductSearchRequest("사과", null, null, SearchSortOption.CATEGORY_BOOSTING_DESC);
        ProductSearchRequest relevanceSortRequest = new ProductSearchRequest("사과", null, null, SearchSortOption.RELEVANCE_DESC);

        List<SearchHitResult> boostedResults = productSearchService.searchPage(categoryBoostSortRequest, pageable).results();
        List<SearchHitResult> relevanceResults = productSearchService.searchPage(relevanceSortRequest, pageable).results();

        Assertions.assertEquals(SearchResultTestSupport.extractIds(relevanceResults), SearchResultTestSupport.extractIds(boostedResults),
                "beta=0이면 카테고리 부스트 룰 영향은 0이어야 하며 CATEGORY_BOOSTING_DESC와 RELEVANCE_DESC 순서는 동일해야 합니다.");
    }

    @AfterEach
    void resetBeta() {
        categoryBoostBetaTuner.reset();
    }

    @AfterAll
    void tearDown() throws Exception {
        deleteAllVersionedIndices();
    }

    private long countCategoryInTopN(List<SearchHitResult> results, int topN, int expectedCategoryId) {
        return results.stream()
                .limit(topN)
                .map(hit -> SearchResultTestSupport.asInteger(hit.source(), "lev3_category_id"))
                .filter(categoryId -> categoryId != null && categoryId == expectedCategoryId)
                .count();
    }

    private org.springframework.data.domain.Pageable pageRequest(int page, int size) {
        return com.example.aisearch.model.search.SearchPagingPolicy.toPageable(page, size);
    }
}
