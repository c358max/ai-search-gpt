package com.example.aisearch;

import com.example.aisearch.service.indexing.orchestration.IndexRolloutService;
import com.example.aisearch.service.indexing.orchestration.result.IndexRolloutResult;
import com.example.aisearch.support.RequiresElasticsearch;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ai-search.index-name=restore-test-products",
                "ai-search.read-alias=restore-test-products-read",
                "ai-search.synonyms-set=restore-test-synonyms",
                "ai-search.index-retention-count=3"
        }
)
@RequiresElasticsearch
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexRestoreIntegrationTest extends RestApiIntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private IndexRolloutService indexRolloutService;

    @BeforeAll
    void setUp() throws Exception {
        printIsolationConfig("IndexRestoreIntegrationTest");
        deleteAllVersionedIndices();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteAllVersionedIndices();
    }

    @Test
    @DisplayName("복구 API는 직전 정상 인덱스로 롤백해 검색 결과를 복구한다")
    void restoreApi는_직전_인덱스로_롤백해_검색결과를_복구한다() throws Exception {
        System.out.println("[STEP-1] baseline rollout 시작");
        IndexRolloutResult baseline = indexRolloutService.rollOutFromSourceData();
        System.out.println("[BASELINE] oldIndex=" + baseline.oldIndex() + ", newIndex=" + baseline.newIndex());

        System.out.println("[STEP-2] baseline 검색 검증 query=치즈");
        JsonNode baselineSearch = search("치즈");
        printSearchResults("BASELINE", "치즈", baselineSearch);
        assertContainsProductName(baselineSearch.path("results"), "치즈");

        Thread.sleep(1100L);
        System.out.println("[STEP-3] broken rollout 시작 dataPath=data/food-products_only10.json");
        IndexRolloutResult broken = indexRolloutService.rollOutFromSourceData("data/food-products_only10.json");
        System.out.println("[BROKEN] oldIndex=" + broken.oldIndex() + ", newIndex=" + broken.newIndex());

        System.out.println("[STEP-4] broken 검색 검증 query=치즈");
        JsonNode brokenSearch = search("치즈");
        printSearchResults("BROKEN", "치즈", brokenSearch);
        assertNotContainsProductName(brokenSearch.path("results"), "치즈");

        System.out.println("[STEP-5] restore candidates 조회");
        JsonNode candidates = listRestoreCandidates();
        JsonNode candidateArray = candidates.path("candidates");
        assertTrue(candidateArray.isArray());
        System.out.println("[CANDIDATES] count=" + candidates.path("count").asInt());
        for (JsonNode candidate : candidateArray) {
            System.out.println("[CANDIDATE] indexName=" + candidate.path("indexName").asText()
                    + ", current=" + candidate.path("current").asBoolean()
                    + ", restorable=" + candidate.path("restorable").asBoolean());
        }

        String targetIndex = null;
        for (JsonNode candidate : candidateArray) {
            if (candidate.path("restorable").asBoolean(false)) {
                targetIndex = candidate.path("indexName").asText();
                break;
            }
        }

        assertEquals(baseline.newIndex(), targetIndex);
        System.out.println("[STEP-6] restore 실행 targetIndex=" + targetIndex);

        JsonNode restoreResponse = restore(targetIndex);
        System.out.println("[RESTORE] success=" + restoreResponse.path("success").asBoolean()
                + ", alias=" + restoreResponse.path("alias").asText()
                + ", oldIndex=" + restoreResponse.path("oldIndex").asText()
                + ", restoredIndex=" + restoreResponse.path("restoredIndex").asText());
        assertTrue(restoreResponse.path("success").asBoolean(false));
        assertEquals(broken.newIndex(), restoreResponse.path("oldIndex").asText());
        assertEquals(baseline.newIndex(), restoreResponse.path("restoredIndex").asText());

        System.out.println("[STEP-7] rollback 후 검색 검증 query=치즈");
        JsonNode restoredSearch = search("치즈");
        printSearchResults("RESTORED", "치즈", restoredSearch);
        assertContainsProductName(restoredSearch.path("results"), "치즈");
    }

    @Test
    @DisplayName("복구 후보 조회 API는 복구 가능한 인덱스 목록을 최신순으로 반환한다")
    void candidatesApi는_복구가능한_인덱스목록을_최신순으로_반환한다() throws Exception {
        IndexRolloutResult baseline = indexRolloutService.rollOutFromSourceData();
        Thread.sleep(1100L);
        IndexRolloutResult broken = indexRolloutService.rollOutFromSourceData("data/food-products_only10.json");

        JsonNode response = listRestoreCandidates();
        JsonNode candidates = response.path("candidates");

        System.out.println("[CANDIDATES-ORDER] baseline=" + baseline.newIndex() + ", broken=" + broken.newIndex());
        for (JsonNode candidate : candidates) {
            System.out.println("[CANDIDATES-ORDER-ITEM] indexName=" + candidate.path("indexName").asText()
                    + ", current=" + candidate.path("current").asBoolean()
                    + ", restorable=" + candidate.path("restorable").asBoolean());
        }

        assertEquals(2, response.path("count").asInt());
        assertEquals(broken.newIndex(), response.path("currentAliasIndex").asText());
        assertEquals(broken.newIndex(), candidates.get(0).path("indexName").asText());
        assertTrue(candidates.get(0).path("current").asBoolean());
        assertFalse(candidates.get(0).path("restorable").asBoolean());
        assertEquals(baseline.newIndex(), candidates.get(1).path("indexName").asText());
        assertFalse(candidates.get(1).path("current").asBoolean());
        assertTrue(candidates.get(1).path("restorable").asBoolean());
    }

    @Test
    @DisplayName("복구 API는 현재 인덱스를 대상으로 요청하면 400을 반환한다")
    void restoreApi는_현재인덱스를_target으로_주면_400을_반환한다() throws Exception {
        IndexRolloutResult current = indexRolloutService.rollOutFromSourceData();

        HttpResponse<String> response = restoreRaw(current.newIndex());
        System.out.println("[RESTORE-400] targetIndex=" + current.newIndex()
                + ", status=" + response.statusCode()
                + ", body=" + response.body());

        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("복구 API는 존재하지 않는 인덱스를 대상으로 요청하면 404를 반환한다")
    void restoreApi는_존재하지않는_인덱스면_404를_반환한다() throws Exception {
        indexRolloutService.rollOutFromSourceData();
        String missingIndex = properties.indexName() + "-v20991231235959";

        HttpResponse<String> response = restoreRaw(missingIndex);
        System.out.println("[RESTORE-404] targetIndex=" + missingIndex
                + ", status=" + response.statusCode()
                + ", body=" + response.body());

        assertEquals(404, response.statusCode());
    }

    private JsonNode listRestoreCandidates() throws Exception {
        return getJsonAndAssertOk("/api/admin/index-restore/candidates");
    }

    private JsonNode restore(String targetIndex) throws Exception {
        String body = """
                {
                  "targetIndex": "%s"
                }
                """.formatted(targetIndex);
        return postJsonAndAssertOk("/api/admin/index-restore", body);
    }

    private HttpResponse<String> restoreRaw(String targetIndex) throws Exception {
        String body = """
                {
                  "targetIndex": "%s"
                }
                """.formatted(targetIndex);
        return postJson("/api/admin/index-restore", body);
    }

    private JsonNode search(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return getJsonAndAssertOk("/api/search?q=" + encodedQuery + "&page=1&size=20&sort=RELEVANCE_DESC");
    }

    private void assertContainsProductName(JsonNode results, String expectedNameKeyword) {
        assertTrue(SearchResultTestSupport.containsProductName(results, expectedNameKeyword),
                "검색 결과에 '" + expectedNameKeyword + "' 상품이 포함되어야 합니다.");
    }

    private void assertNotContainsProductName(JsonNode results, String unexpectedKeyword) {
        assertFalse(SearchResultTestSupport.containsProductName(results, unexpectedKeyword),
                "검색 결과에 '" + unexpectedKeyword + "' 상품이 포함되면 안 됩니다.");
    }

    private void printSearchResults(String stage, String query, JsonNode searchJson) {
        JsonNode results = searchJson.path("results");
        System.out.println("[SEARCH-" + stage + "] query=" + query
                + ", totalElements=" + searchJson.path("totalElements").asLong()
                + ", totalPages=" + searchJson.path("totalPages").asInt()
                + ", count=" + searchJson.path("count").asInt());

        for (int i = 0; i < results.size(); i++) {
            JsonNode hit = results.get(i);
            JsonNode source = hit.path("source");
            System.out.println("[SEARCH-" + stage + "-HIT] rank=" + (i + 1)
                    + ", score=" + hit.path("score").asDouble()
                    + ", id=" + hit.path("id").asText()
                    + ", name=" + source.path("product_name").asText()
                    + ", category=" + source.path("category").asText());
        }
    }

    @Override
    protected int port() {
        return port;
    }
}
