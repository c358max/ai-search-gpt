package com.example.aisearch.service.search.strategy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.model.search.ProductSearchRequest;
import com.example.aisearch.model.search.SearchPageResult;
import com.example.aisearch.service.embedding.QueryEmbeddingService;
import com.example.aisearch.service.embedding.QueryEmbeddingUnavailableException;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostBetaTuner;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostingDecider;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostingResult;
import com.example.aisearch.service.search.query.HybridBaseQueryBuilder;
import com.example.aisearch.service.search.query.SearchFilterQueryBuilder;
import com.example.aisearch.service.search.strategy.mapper.DefaultSearchResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * BM25 가중치를 섞지 않고 벡터 유사도만으로 검색하는 전략.
 *
 * <p>검색어가 있을 때:
 * - filter 또는 match_all 을 base query 로 사용
 * - cosineSimilarity(product_vector, query_vector) 만으로 점수를 계산
 *
 * <p>검색어가 없을 때:
 * - 기존과 동일하게 필터/정렬 기반 일반 검색을 수행한다.
 */
@Component
@ConditionalOnProperty(prefix = "ai-search.search", name = "mode", havingValue = "vector-only")
public class VectorOnlySearchStrategy implements SearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(VectorOnlySearchStrategy.class);

    private static final String VECTOR_ONLY_SCRIPT = """
            double vectorScore = (cosineSimilarity(params.query_vector, 'product_vector') + 1.0) / 2.0;
            if (vectorScore < params.min_score_threshold) return 0.0;
            double categoryBoost = 0.0;
            if (params.category_boost_by_id != null && doc['primary_lev3_category_id'].size() != 0) {
              String categoryKey = String.valueOf(doc['primary_lev3_category_id'].value);
              def rawBoost = params.category_boost_by_id.get(categoryKey);
              if (rawBoost != null) {
                categoryBoost = ((Number) rawBoost).doubleValue();
              }
            }
            return vectorScore * (1.0 + params.beta * categoryBoost);
            """;

    private final ElasticsearchClient client;
    private final AiSearchProperties properties;
    private final QueryEmbeddingService queryEmbeddingService;
    private final SearchFilterQueryBuilder filterQueryBuilder;
    private final HybridBaseQueryBuilder hybridBaseQueryBuilder;
    private final DefaultSearchResponseMapper searchResponseMapper;
    private final CategoryBoostingDecider categoryBoostingDecider;
    private final CategoryBoostBetaTuner categoryBoostBetaTuner;

    public VectorOnlySearchStrategy(
            ElasticsearchClient client,
            AiSearchProperties properties,
            QueryEmbeddingService queryEmbeddingService,
            SearchFilterQueryBuilder filterQueryBuilder,
            HybridBaseQueryBuilder hybridBaseQueryBuilder,
            DefaultSearchResponseMapper searchResponseMapper,
            CategoryBoostingDecider categoryBoostingDecider,
            CategoryBoostBetaTuner categoryBoostBetaTuner
    ) {
        this.client = client;
        this.properties = properties;
        this.queryEmbeddingService = queryEmbeddingService;
        this.filterQueryBuilder = filterQueryBuilder;
        this.hybridBaseQueryBuilder = hybridBaseQueryBuilder;
        this.searchResponseMapper = searchResponseMapper;
        this.categoryBoostingDecider = categoryBoostingDecider;
        this.categoryBoostBetaTuner = categoryBoostBetaTuner;
    }

    @Override
    public SearchPageResult search(ProductSearchRequest searchRequest, Pageable pageable) {
        try {
            if (searchRequest.hasQuery()) {
                return vectorOnlySearch(searchRequest, pageable);
            }
            return filterOnlySearch(searchRequest, pageable);
        } catch (IOException e) {
            log.error("Search request failed. query={}, alias={}", searchRequest.query(), getReadAlias(), e);
            throw new IllegalStateException("검색 요청 실패: " + summarizeException(e), e);
        }
    }

    private SearchPageResult vectorOnlySearch(ProductSearchRequest request, Pageable pageable) throws IOException {
        CategoryBoostingResult decision = categoryBoostingDecider.decide(request);
        Query baseQuery = filterQueryBuilder.buildRootQuery(request);

        try {
            co.elastic.clients.elasticsearch.core.SearchRequest esSearchRequest =
                    co.elastic.clients.elasticsearch.core.SearchRequest.of(s -> s
                            .index(getReadAlias())
                            .query(q -> q.scriptScore(ss -> ss
                                    .query(baseQuery)
                                    .script(sc -> sc.inline(i -> {
                                        i.lang("painless")
                                                .source(VECTOR_ONLY_SCRIPT)
                                                .params("query_vector", JsonData.of(queryEmbeddingService.toQueryEmbedding(request.query())))
                                                .params("min_score_threshold", JsonData.of(properties.minScoreThreshold()))
                                                .params("beta", JsonData.of(categoryBoostBetaTuner.getBeta()));
                                        if (decision.applyCategoryBoost()) {
                                            i.params("category_boost_by_id", JsonData.of(decision.categoryBoostById()));
                                        }
                                        return i;
                                    }))
                            ))
                            .sort(decision.sortOptions())
                            .trackScores(true)
                            .from((int) pageable.getOffset())
                            .size(pageable.getPageSize())
                            .minScore(properties.minScoreThreshold())
                    );

            SearchResponse<Map> response = client.search(esSearchRequest, Map.class);
            return searchResponseMapper.toPageResult(response, pageable);
        } catch (QueryEmbeddingUnavailableException e) {
            log.warn("Query embedding unavailable. Falling back to lexical search. query={}", request.query(), e);
            return lexicalFallbackSearch(request, pageable, decision);
        }
    }

    private SearchPageResult filterOnlySearch(ProductSearchRequest request, Pageable pageable) throws IOException {
        co.elastic.clients.elasticsearch.core.SearchRequest esSearchRequest =
                co.elastic.clients.elasticsearch.core.SearchRequest.of(s -> s
                        .index(getReadAlias())
                        .query(filterQueryBuilder.buildRootQuery(request))
                        .sort(request.sortOption().toSortOptions())
                        .trackScores(true)
                        .from((int) pageable.getOffset())
                        .size(pageable.getPageSize())
                );

        SearchResponse<Map> response = client.search(esSearchRequest, Map.class);
        return searchResponseMapper.toPageResult(response, pageable);
    }

    private SearchPageResult lexicalFallbackSearch(
            ProductSearchRequest request,
            Pageable pageable,
            CategoryBoostingResult decision
    ) throws IOException {
        Query lexicalFallbackQuery = hybridBaseQueryBuilder.buildLexicalFallback(
                request,
                filterQueryBuilder.buildFilterQuery(request)
        );
        co.elastic.clients.elasticsearch.core.SearchRequest esSearchRequest =
                co.elastic.clients.elasticsearch.core.SearchRequest.of(s -> s
                        .index(getReadAlias())
                        .query(lexicalFallbackQuery)
                        .sort(decision.sortOptions())
                        .trackScores(true)
                        .from((int) pageable.getOffset())
                        .size(pageable.getPageSize())
                );

        SearchResponse<Map> response = client.search(esSearchRequest, Map.class);
        return searchResponseMapper.toPageResult(response, pageable);
    }

    private String getReadAlias() {
        String readAlias = properties.readAlias();
        if (readAlias == null || readAlias.isBlank()) {
            throw new IllegalStateException("ai-search.read-alias 값이 비어 있습니다.");
        }
        return readAlias;
    }

    private String summarizeException(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }
}
