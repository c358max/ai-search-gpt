package com.example.aisearch;

import com.example.aisearch.service.indexing.orchestration.IndexRolloutService;
import com.example.aisearch.service.synonym.SynonymReloadMode;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ai-search.index-name=synonym-rest-it-products",
                "ai-search.read-alias=synonym-rest-it-products-read",
                "ai-search.synonyms-set=synonym-rest-it-synonyms"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynonymsRestClientIntegrationTest extends RestApiIntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private IndexRolloutService indexRolloutService;

    @BeforeAll
    void setUp() throws Exception {
        printIsolationConfig("SynonymsRestClientIntegrationTest");
        deleteAllVersionedIndices();
        indexRolloutService.rollOutFromSourceData();
    }

    @AfterAll
    void restoreProductionSynonyms() throws Exception {
        try {
            reloadSynonyms(SynonymReloadMode.PRODUCTION);
        } catch (Exception ignored) {
            // 테스트 본문의 실패 원인을 가리지 않기 위해 정리 단계 예외는 무시
        }
        deleteAllVersionedIndices();
    }

    @Test
    void 회귀동의어_적용후_딤섬_검색시_만두가_포함된다() throws Exception {
        reloadSynonymsAndAssert(SynonymReloadMode.REGRESSION);
        assertSynonymSearchContainsProduct("딤섬", "만두");
    }

    @Test
    void 동의어_적용후_교자_검색시_만두가_포함된다() throws Exception {
        reloadSynonymsAndAssert(SynonymReloadMode.PRODUCTION);
        assertSynonymSearchContainsProduct("교자", "만두");
    }


    @Test
    void 동의어_적용후_얄피_검색시_생만두가_포함된다() throws Exception {
        reloadSynonymsAndAssert(SynonymReloadMode.PRODUCTION);
        assertSynonymSearchContainsProduct("얄피", "만두");
    }

    private HttpResponse<String> reloadSynonyms(SynonymReloadMode mode) throws Exception {
        String body = """
                {
                  "mode": "%s"
                }
                """.formatted(mode.name());
        return postJson("/api/search/reload-synonyms", body);
    }

    private HttpResponse<String> search(String query, int size) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return get("/api/search?q=" + encodedQuery + "&page=1&size=" + size + "&sort=RELEVANCE_DESC");
    }

    private JsonNode reloadSynonymsAndAssert(SynonymReloadMode mode) throws Exception {
        HttpResponse<String> reloadResponse = reloadSynonyms(mode);
        assertEquals(200, reloadResponse.statusCode());
        JsonNode reloadJson = readJson(reloadResponse);
        assertTrue(reloadJson.path("updated").asBoolean(false));
        assertTrue(reloadJson.path("reloaded").asBoolean(false));
        assertEquals(mode.name(), reloadJson.path("mode").asText());
        return reloadJson;
    }

    private JsonNode searchAndAssertOk(String query, int size) throws Exception {
        HttpResponse<String> searchResponse = search(query, size);
        assertEquals(200, searchResponse.statusCode());
        JsonNode searchJson = readJson(searchResponse);
        assertTrue(searchJson.path("results").isArray());
        return searchJson;
    }

    private void assertContainsProductName(JsonNode results, String expectedNameKeyword) {
        boolean containsExpectedProduct = false;
        for (JsonNode hit : results) {
            String name = hit.path("source").path("goods_name").asText("");
            if (name.contains(expectedNameKeyword)) {
                containsExpectedProduct = true;
                break;
            }
        }
        assertTrue(containsExpectedProduct, "검색 결과에 '" + expectedNameKeyword + "' 상품이 포함되어야 합니다.");
    }

    private void printSearchResults(String query, JsonNode searchJson, String targetKeyword) {
        JsonNode results = searchJson.path("results");
        System.out.println("[SYNONYM_SEARCH] query=" + query
                + ", totalElements=" + searchJson.path("totalElements").asLong()
                + ", totalPages=" + searchJson.path("totalPages").asInt()
                + ", count=" + searchJson.path("count").asInt()
                + ", targetKeyword=" + targetKeyword);

        long printed = java.util.stream.IntStream.range(0, results.size())
                .mapToObj(i -> new HitView(i, results.get(i)))
                .filter(hitView -> hitView.productName().contains(targetKeyword))
                .peek(hitView -> System.out.println("rank=" + hitView.rank()
                        + ", score=" + hitView.hit().path("score").asDouble()
                        + ", id=" + hitView.hit().path("id").asText()
                        + ", name=" + hitView.productName()
                        + ", category=" + hitView.source().path("lev3_category_id_name").asText()
                        + ", price=" + hitView.source().path("sale_price").asText()))
                .count();

        if (printed == 0) {
            System.out.println("[SYNONYM_SEARCH] target keyword not found in printed results");
        }
    }

    private void assertSynonymSearchContainsProduct(
            String query,
        String expectedProductNameKeyword
    ) throws Exception {
        JsonNode searchJson = searchAndAssertOk(query, 20);
        printSearchResults(query, searchJson, expectedProductNameKeyword);
        assertContainsProductName(searchJson.path("results"), expectedProductNameKeyword);
    }

    private record HitView(int index, JsonNode hit) {
        int rank() {
            return index + 1;
        }

        JsonNode source() {
            return hit.path("source");
        }

        String productName() {
            return source().path("goods_name").asText("");
        }
    }

    @Override
    protected int port() {
        return port;
    }
}
