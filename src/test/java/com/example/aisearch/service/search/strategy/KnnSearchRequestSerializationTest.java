package com.example.aisearch.service.search.strategy;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.model.search.SearchSortOption;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostBetaTuner;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostingResult;
import com.example.aisearch.service.search.query.HybridBaseQueryBuilder;
import com.example.aisearch.service.search.query.SearchFilterQueryBuilder;
import com.example.aisearch.service.search.strategy.request.ElasticsearchSearchRequestBuilder;
import com.example.aisearch.service.search.strategy.script.PainlessHybridScoreScriptFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.json.stream.JsonGenerator;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnnSearchRequestSerializationTest {
    // 직렬화 구조 검증용 더미 벡터 (값 자체의 의미는 없다)
    private static final List<Float> DUMMY_QUERY_VECTOR = List.of(0.11f, 0.22f, 0.33f);
    // 필터 직렬화 검증용 더미 카테고리 ID
    private static final List<Integer> DUMMY_CATEGORY_IDS = List.of(4);

    private final SearchFilterQueryBuilder filterQueryBuilder = new SearchFilterQueryBuilder();
    private final HybridBaseQueryBuilder hybridBaseQueryBuilder = new HybridBaseQueryBuilder();
    private final CategoryBoostBetaTuner categoryBoostBetaTuner = new CategoryBoostBetaTuner();
    private final AiSearchProperties properties = new AiSearchProperties(
            "http://localhost:9200",
            "elastic",
            "password",
            "food-products",
            "food-products-read",
            "food-synonyms",
            "classpath:es/dictionary/synonyms_ko.txt",
            "classpath:es/dictionary/synonyms_kr_regression.txt",
            "djl://dummy",
            "classpath:/dummy",
            0.74,
            300,
            3
    );
    private final ElasticsearchSearchRequestBuilder searchRequestBuilder =
            new ElasticsearchSearchRequestBuilder(
                    properties,
                    categoryBoostBetaTuner,
                    new PainlessHybridScoreScriptFactory()
            );

    @Test
    void shouldPrintSerializedSearchRequestJsonForTwoRepresentativeCases() throws Exception {
        com.example.aisearch.model.search.ProductSearchRequest appleRequest =
                new com.example.aisearch.model.search.ProductSearchRequest(
                        "사과",
                        null,
                        null,
                        SearchSortOption.CATEGORY_BOOSTING_DESC
                );
        CategoryBoostingResult boostDecision = CategoryBoostingResult.withBoost(Map.of("4", 0.2));
        Query appleBaseQuery = hybridBaseQueryBuilder.build(appleRequest, filterQueryBuilder.buildFilterQuery(appleRequest));
        SearchRequest appleEsRequest = searchRequestBuilder.buildHybridRequest(
                "food-products-read",
                appleBaseQuery,
                boostDecision,
                DUMMY_QUERY_VECTOR,
                0,
                20
        );

        com.example.aisearch.model.search.ProductSearchRequest filterOnlyRequest =
                new com.example.aisearch.model.search.ProductSearchRequest(
                        null,
                        null,
                        DUMMY_CATEGORY_IDS,
                        SearchSortOption.RELEVANCE_DESC
                );
        Query filterOnlyRootQuery = filterQueryBuilder.buildRootQuery(filterOnlyRequest);
        SearchRequest filterOnlyEsRequest = searchRequestBuilder.buildFilterOnlyRequest(
                "food-products-read",
                filterOnlyRootQuery,
                filterOnlyRequest.sortOption(),
                0,
                20
        );

        String appleJson = toPrettyJson(appleEsRequest);
        String filterOnlyJson = toPrettyJson(filterOnlyEsRequest);

        System.out.println("=== CASE A: apple + CATEGORY_BOOSTING_DESC ===");
        System.out.println(formatForConsole(appleJson));
        System.out.println("=== CASE B: no query + categoryId filter + RELEVANCE_DESC ===");
        System.out.println(formatForConsole(filterOnlyJson));

        assertTrue(appleJson.contains("\"script_score\""));
        assertTrue(appleJson.contains("\"category_boost_by_id\""));
        assertTrue(appleJson.contains("\"min_score_threshold\""));
        assertTrue(appleJson.contains("\"beta\""));
        assertTrue(!appleJson.contains("Math.min(1.0"));
        assertTrue(filterOnlyJson.contains("\"terms\""));
        assertTrue(!filterOnlyJson.contains("\"script_score\""));
    }

    private static String toPrettyJson(SearchRequest request) throws Exception {
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        StringWriter writer = new StringWriter();
        JsonGenerator generator = mapper.jsonProvider().createGenerator(writer);
        request.serialize(generator, mapper);
        generator.close();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readTree(writer.toString()));
    }

    private static String formatForConsole(String prettyJson) {
        String withNewLines = prettyJson.replace("\\n", "\n");
        String scriptSource = extractScriptSource(withNewLines);
        if (scriptSource == null || scriptSource.isBlank()) {
            return withNewLines;
        }
        return withNewLines
                + "\n\n--- SCRIPT SOURCE (READABLE) ---\n"
                + indent(scriptSource, "  ");
    }

    private static String extractScriptSource(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode sourceNode = root.path("query")
                    .path("script_score")
                    .path("script")
                    .path("source");
            if (sourceNode.isMissingNode() || sourceNode.isNull()) {
                return null;
            }
            return sourceNode.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String indent(String text, String prefix) {
        return text.lines()
                .map(line -> prefix + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
