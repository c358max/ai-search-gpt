package com.example.aisearch;

import com.example.aisearch.model.SearchHitResult;
import com.example.aisearch.model.search.SearchPageResult;
import com.example.aisearch.model.search.SearchPagingPolicy;
import com.example.aisearch.model.search.SearchPrice;
import com.example.aisearch.model.search.ProductSearchRequest;
import com.example.aisearch.model.search.SearchSortOption;
import com.example.aisearch.service.indexing.orchestration.IndexRolloutService;
import com.example.aisearch.service.search.ProductSearchService;
import com.example.aisearch.support.RequiresElasticsearch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(properties = {
        "ai-search.index-name=search-it-products",
        "ai-search.read-alias=search-it-products-read",
        "ai-search.synonyms-set=search-it-synonyms"
})
@RequiresElasticsearch
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchIntegrationTest extends ElasticsearchIntegrationTestBase {

    private static final List<Integer> TEST_CATEGORY_IDS = List.of(5675, 5711, 5721);
    private static final int APPLE_TEST_CATEGORY_ID = 5711;

    @Autowired
    private IndexRolloutService indexRolloutService;

    @Autowired
    private ProductSearchService productSearchService;

    @BeforeAll
    void setUp() throws Exception {
        printIsolationConfig("SearchIntegrationTest");
        deleteAllVersionedIndices();
        var rollout = indexRolloutService.rollOutFromSourceData();
        long indexed = rollout.indexedCount();
        Assertions.assertTrue(indexed >= 100, "최소 100건 이상 인덱싱되어야 합니다.");
        System.out.println("[INDEXED] oldIndex=" + rollout.oldIndex()
                + ", newIndex=" + rollout.newIndex()
                + ", total=" + indexed);
    }

    @AfterAll
    void tearDown() throws Exception {
        deleteAllVersionedIndices();
    }

    @Test
    @DisplayName("어린이 건강 간식 검색은 간식 카테고리의 관련 상품을 반환한다")
    void semanticSearchShouldReturnRelevantProducts() {
        // 아이 간식 관련 쿼리 테스트
        String query = "어린이가 먹기 좋은 건강한 간식";
        String[] expectedCategoryKeywords = {"간식"};

        assertSemanticSearchContainsCategories(query, 5, SearchPagingPolicy.DEFAULT_PAGE, expectedCategoryKeywords);
    }

    @Test
    @DisplayName("생새우 해산물 검색은 관련 결과를 반환한다")
    void semanticSearchShouldReturnRelevantProducts2() {
        // 수산물 관련 쿼리 테스트
        String query = "생새우 해산물";
        assertSemanticSearchReturnsResults(query, 20, SearchPagingPolicy.DEFAULT_PAGE);
    }

    @Test
    @DisplayName("임계값 이하의 낮은 관련도 검색어는 빈 결과를 반환한다")
    void semanticSearchShouldReturnEmptyWhenBelowThreshold() {
        // 관련성이 낮은 키워드는 결과가 비어야 함
        String query = "태풍";

        List<SearchHitResult> results = productSearchService.searchPage(new ProductSearchRequest(query, null, null, null), pageRequest(1, 5)).results();

        System.out.println("[SEARCH] query=" + query);
        results.forEach(hit -> System.out.printf(
                "rank=?, score=%s, id=%s, name=%s, category=%s%n",
                hit.score(),
                hit.id(),
                hit.source().get("goods_name"),
                hit.source().get("lev3_category_id_name")
        ));

        Assertions.assertTrue(results.isEmpty(), "MIN_SCORE_THRESHOLD 이하이면 검색 결과가 없어야 합니다.");
    }

    @Test
    @DisplayName("대표 검색어 5개에 대한 상위 5개 결과를 출력한다")
    void semanticSearchFiveQueriesTop5() {
        // 모델 비교용 5개 쿼리 결과를 출력
        String[] queries = {
                "어린이 간식으로 좋은 전통 과자",
                "다이어트에 좋은 저당 간식",
                "단백질 많은 간편식",
                "신선한 해산물 반찬",
                "채식 위주의 건강식"
        };

        int size = 5;
        for (String query : queries) {
            List<SearchHitResult> results = productSearchService.searchPage(new ProductSearchRequest(query, null, null, null), pageRequest(1, size)).results();
            System.out.println("[COMPARE_QUERY] " + query);
            for (int i = 0; i < results.size(); i++) {
                SearchHitResult hit = results.get(i);
                System.out.println("rank=" + (i + 1)
                        + ", score=" + hit.score()
                        + ", id=" + hit.id()
                        + ", name=" + hit.source().get("goods_name")
                        + ", category=" + hit.source().get("lev3_category_id_name"));
            }
        }
    }

    @Test
    @DisplayName("카테고리 필터를 적용하면 요청한 카테고리만 반환한다")
    void categoryFilterShouldReturnOnlyRequestedCategories() {
        ProductSearchRequest request = new ProductSearchRequest(null, null, TEST_CATEGORY_IDS, null);
        List<SearchHitResult> results = productSearchService.searchPage(request, pageRequest(1, 10)).results();

        System.out.println("[CATEGORY_FILTER] categories=" + TEST_CATEGORY_IDS);
        results.forEach(hit -> System.out.printf(
                "id=%s, name=%s, categoryId=%s, price=%s%n",
                hit.id(),
                hit.source().get("goods_name"),
                hit.source().get("lev3_category_id"),
                hit.source().get("sale_price")
        ));

        Assertions.assertFalse(results.isEmpty(), "카테고리 필터 결과는 비어있으면 안 됩니다.");
        Assertions.assertTrue(results.stream().allMatch(hit -> {
            Integer categoryId = SearchResultTestSupport.asCategoryInteger(hit.source());
            return categoryId != null && TEST_CATEGORY_IDS.contains(categoryId);
        }), "모든 결과의 primary_lev3_category_id는 테스트 카테고리 중 하나여야 합니다.");
    }

    @Test
    @DisplayName("가격 범위 필터를 적용하면 범위 안의 상품만 반환한다")
    void priceRangeFilterShouldReturnOnlyInRange() {
        SearchPrice price = new SearchPrice(5000, 15000);
        ProductSearchRequest request = new ProductSearchRequest(null, price, null, null);
        List<SearchHitResult> results = productSearchService.searchPage(request, pageRequest(1, 10)).results();

        System.out.println("[PRICE_FILTER] min=5000, max=15000");
        results.forEach(hit -> System.out.printf(
                "id=%s, name=%s, categoryId=%s, price=%s%n",
                hit.id(),
                hit.source().get("goods_name"),
                hit.source().get("lev3_category_id"),
                hit.source().get("sale_price")
        ));

        Assertions.assertFalse(results.isEmpty(), "가격 범위 필터 결과는 비어있으면 안 됩니다.");
        Assertions.assertTrue(results.stream().allMatch(hit -> {
            Integer priceValue = asInteger(hit.source(), "sale_price");
            return priceValue != null && priceValue >= 5000 && priceValue <= 15000;
        }), "모든 결과의 sale_price는 5000~15000 범위여야 합니다.");
    }

    @Test
    @DisplayName("검색어와 카테고리와 가격 조건을 함께 적용하면 모두 만족하는 결과만 반환한다")
    void keywordCategoryAndPriceFilterShouldReturnMatchingResults() {
        ProductSearchRequest request = new ProductSearchRequest(
                "사과",
                new SearchPrice(5000, 30000),
                List.of(APPLE_TEST_CATEGORY_ID),
                SearchSortOption.RELEVANCE_DESC
        );
        List<SearchHitResult> results = productSearchService.searchPage(request, pageRequest(1, 10)).results();

        System.out.println("[COMBINED_FILTER] query=사과, category=" + APPLE_TEST_CATEGORY_ID + ", min=5000, max=30000");
        results.forEach(hit -> System.out.printf(
                "id=%s, score=%s, name=%s, categoryId=%s, price=%s%n",
                hit.id(),
                hit.score(),
                hit.source().get("goods_name"),
                hit.source().get("lev3_category_id"),
                hit.source().get("sale_price")
        ));

        Assertions.assertFalse(results.isEmpty(), "복합 조건 결과는 비어있으면 안 됩니다.");
        Assertions.assertTrue(results.stream().allMatch(hit -> {
            Integer categoryId = SearchResultTestSupport.asCategoryInteger(hit.source());
            Integer priceValue = SearchResultTestSupport.asInteger(hit.source(), "sale_price");
            return categoryId != null
                    && categoryId == APPLE_TEST_CATEGORY_ID
                    && priceValue != null
                    && priceValue >= 5000
                    && priceValue <= 30000;
        }), "모든 결과가 카테고리/가격 조건을 충족해야 합니다.");
    }

    @Test
    @DisplayName("가격 오름차순 정렬은 결과를 낮은 가격부터 반환한다")
    void priceAscSortShouldOrderByPrice() {
        ProductSearchRequest request = new ProductSearchRequest(
                "간식",
                new SearchPrice(0, 30000),
                TEST_CATEGORY_IDS,
                SearchSortOption.PRICE_ASC
        );

        List<SearchHitResult> results = productSearchService.searchPage(request, pageRequest(1, 10)).results();
        List<Integer> prices = extractPrices(results);
        Assertions.assertFalse(prices.isEmpty(), "가격 오름차순 검증을 위한 결과가 필요합니다.");
        assertNonDecreasing(prices);
    }

    @Test
    @DisplayName("가격 내림차순 정렬은 결과를 높은 가격부터 반환한다")
    void priceDescSortShouldOrderByPrice() {
        ProductSearchRequest request = new ProductSearchRequest(
                "간식",
                new SearchPrice(0, 30000),
                TEST_CATEGORY_IDS,
                SearchSortOption.PRICE_DESC
        );

        List<SearchHitResult> results = productSearchService.searchPage(request, pageRequest(1, 10)).results();
        List<Integer> prices = extractPrices(results);
        Assertions.assertFalse(prices.isEmpty(), "가격 내림차순 검증을 위한 결과가 필요합니다.");
        assertNonIncreasing(prices);
    }

    @Test
    @DisplayName("정렬 옵션을 생략하면 명시적 연관도순 정렬과 같은 결과를 반환한다")
    void defaultSortShouldMatchExplicitRelevanceSort() {
        ProductSearchRequest defaultSortRequest = new ProductSearchRequest(
                "건강한 간식",
                new SearchPrice(0, 30000),
                TEST_CATEGORY_IDS,
                null
        );
        ProductSearchRequest explicitRelevanceRequest = new ProductSearchRequest(
                "건강한 간식",
                new SearchPrice(0, 30000),
                TEST_CATEGORY_IDS,
                SearchSortOption.RELEVANCE_DESC
        );

        Pageable pageable = pageRequest(1, 10);
        List<SearchHitResult> defaultResults = productSearchService.searchPage(defaultSortRequest, pageable).results();
        List<SearchHitResult> explicitResults = productSearchService.searchPage(explicitRelevanceRequest, pageable).results();

        List<String> defaultIds = defaultResults.stream().map(SearchHitResult::id).collect(Collectors.toList());
        List<String> explicitIds = explicitResults.stream().map(SearchHitResult::id).collect(Collectors.toList());
        Assertions.assertEquals(explicitIds, defaultIds, "정렬 미지정 시 기본값은 연관도 정렬이어야 합니다.");
    }

    @Test
    @DisplayName("엔진 정렬에서도 페이지와 사이즈에 따라 안정적인 슬라이스를 반환한다")
    void pageAndSizeShouldReturnStableSlicesInEngineSort() {
        ProductSearchRequest page1Request = new ProductSearchRequest(
                null,
                new SearchPrice(0, 30000),
                TEST_CATEGORY_IDS,
                SearchSortOption.PRICE_ASC
        );
        ProductSearchRequest page2Request = new ProductSearchRequest(
                null,
                new SearchPrice(0, 30000),
                TEST_CATEGORY_IDS,
                SearchSortOption.PRICE_ASC
        );

        List<SearchHitResult> page1Results = productSearchService.searchPage(page1Request, pageRequest(1, 5)).results();
        List<SearchHitResult> page2Results = productSearchService.searchPage(page2Request, pageRequest(2, 5)).results();

        Assertions.assertFalse(page1Results.isEmpty(), "1페이지 결과가 필요합니다.");
        Assertions.assertFalse(page2Results.isEmpty(), "2페이지 결과가 필요합니다.");

        List<String> page1Ids = page1Results.stream().map(SearchHitResult::id).toList();
        List<String> page2Ids = page2Results.stream().map(SearchHitResult::id).toList();
        Assertions.assertTrue(page1Ids.stream().noneMatch(page2Ids::contains), "페이지 간 결과 ID는 중복되면 안 됩니다.");

        List<Integer> page1Prices = SearchResultTestSupport.extractIntegers(page1Results, "sale_price");
        List<Integer> page2Prices = SearchResultTestSupport.extractIntegers(page2Results, "sale_price");
        assertNonDecreasing(page1Prices);
        assertNonDecreasing(page2Prices);
        Assertions.assertEquals(page1Results.size(), page1Prices.size(), "1페이지 결과에는 가격 정보가 모두 있어야 합니다.");
        Assertions.assertEquals(page2Results.size(), page2Prices.size(), "2페이지 결과에는 가격 정보가 모두 있어야 합니다.");
        Assertions.assertTrue(page1Prices.get(page1Prices.size() - 1) <= page2Prices.get(0),
                "페이지 경계에서도 오름차순이 유지되어야 합니다.");
    }

    @Test
    @DisplayName("조사가 포함된 자연어 검색어도 관련 결과를 반환한다")
    void koreanParticleQueryShouldStillReturnRelevantCategory() {
        ProductSearchRequest request = new ProductSearchRequest("어린이가 먹을 간식을 추천해줘", null, null, SearchSortOption.RELEVANCE_DESC);
        SearchPageResult pageResult = productSearchService.searchPage(request, pageRequest(1, 5));
        List<SearchHitResult> results = pageResult.results();

        System.out.println("[MORPH] query=어린이가 먹을 간식을 추천해줘");
        System.out.println("[MORPH_PAGE] page=" + pageResult.page()
                + ", size=" + pageResult.size()
                + ", totalElements=" + pageResult.totalElements()
                + ", totalPages=" + pageResult.totalPages());
        results.forEach(hit -> System.out.printf(
                "rank=?, score=%s, id=%s, name=%s, category=%s%n",
                hit.score(),
                hit.id(),
                hit.source().get("goods_name"),
                hit.source().get("lev3_category_id_name")
        ));

        Assertions.assertFalse(results.isEmpty(), "조사 포함 쿼리에서도 검색 결과가 있어야 합니다.");
        Assertions.assertTrue(pageResult.totalElements() > 0, "조사 포함 쿼리의 전체 결과 수가 0이면 안 됩니다.");
    }

    private void assertSemanticSearchContainsCategories(String query, int size, int page, String... expectedCategoryKeywords) {
        // 검색 결과 출력 및 기대 카테고리 포함 여부 검증
        ProductSearchRequest request = new ProductSearchRequest(query, null, null, null);
        SearchPageResult pageResult = productSearchService.searchPage(request, pageRequest(page, size));
        List<SearchHitResult> results = pageResult.results();

        System.out.println("[SEARCH] query=" + query);
        System.out.println("[PAGE] page=" + pageResult.page()
                + ", size=" + pageResult.size()
                + ", totalElements=" + pageResult.totalElements()
                + ", totalPages=" + pageResult.totalPages());
        for (int i = 0; i < results.size(); i++) {
            SearchHitResult hit = results.get(i);
            System.out.println("rank=" + (i + 1)
                    + ", score=" + hit.score()
                    + ", id=" + hit.id()
                    + ", name=" + hit.source().get("goods_name")
                    + ", category=" + hit.source().get("lev3_category_id_name"));
        }

        Assertions.assertFalse(results.isEmpty(), "검색 결과가 비어있으면 안됩니다.");
        results.forEach(hit -> System.out.printf(
                "[CATEGORY_CHECK] category=%s, name=%s, score=%s%n",
                hit.source().get("lev3_category_id_name"),
                hit.source().get("goods_name"),
                hit.score()
        ));
        boolean containsExpectedCategory = results.stream()
                .map(hit -> (String) hit.source().get("lev3_category_id_name"))
                .anyMatch(category -> containsAnyKeyword(category, expectedCategoryKeywords));
        Assertions.assertTrue(containsExpectedCategory, "상위 결과에 기대 카테고리가 포함되어야 합니다.");
    }

    private void assertSemanticSearchContainsCategories(String query, int size, String... expectedCategoryKeywords) {
        assertSemanticSearchContainsCategories(query, size, SearchPagingPolicy.DEFAULT_PAGE, expectedCategoryKeywords);
    }

    private void assertSemanticSearchReturnsResults(String query, int size, int page) {
        ProductSearchRequest request = new ProductSearchRequest(query, null, null, null);
        SearchPageResult pageResult = productSearchService.searchPage(request, pageRequest(page, size));
        List<SearchHitResult> results = pageResult.results();
        System.out.println("[SEARCH_ONLY] query=" + query
                + ", page=" + pageResult.page()
                + ", size=" + pageResult.size()
                + ", totalElements=" + pageResult.totalElements()
                + ", totalPages=" + pageResult.totalPages());
        Assertions.assertFalse(results.isEmpty(), "검색 결과가 비어있으면 안됩니다.");
    }

    private boolean containsAnyKeyword(String category, String... keywords) {
        // 카테고리 문자열에 키워드가 포함되는지 체크
        if (category == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (category.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Integer asInteger(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private List<Integer> extractPrices(List<SearchHitResult> results) {
        return results.stream()
                .map(hit -> asInteger(hit.source(), "sale_price"))
                .filter(value -> value != null)
                .toList();
    }

    private void assertNonDecreasing(List<Integer> numbers) {
        for (int i = 1; i < numbers.size(); i++) {
            Assertions.assertTrue(numbers.get(i - 1) <= numbers.get(i),
                    "오름차순 위배: " + numbers.get(i - 1) + " > " + numbers.get(i));
        }
    }

    private void assertNonIncreasing(List<Integer> numbers) {
        for (int i = 1; i < numbers.size(); i++) {
            Assertions.assertTrue(numbers.get(i - 1) >= numbers.get(i),
                    "내림차순 위배: " + numbers.get(i - 1) + " < " + numbers.get(i));
        }
    }

    private Pageable pageRequest(int page, int size) {
        return SearchPagingPolicy.toPageable(page, size);
    }

}
