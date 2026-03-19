package com.example.aisearch.service.search.strategy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.embedding.QueryEmbeddingService;
import com.example.aisearch.service.embedding.QueryEmbeddingUnavailableException;
import com.example.aisearch.model.search.SearchPageResult;
import com.example.aisearch.model.search.ProductSearchRequest;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostingDecider;
import com.example.aisearch.service.search.categoryboost.policy.CategoryBoostingResult;
import com.example.aisearch.service.search.query.HybridBaseQueryBuilder;
import com.example.aisearch.service.search.query.SearchFilterQueryBuilder;
import com.example.aisearch.service.search.strategy.mapper.DefaultSearchResponseMapper;
import com.example.aisearch.service.search.strategy.request.ElasticsearchSearchRequestBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * 검색 요청을 벡터 기반 하이브리드 검색 또는 필터 전용 검색으로 분기해 실행하는 전략.
 */
@Component
@ConditionalOnProperty(prefix = "ai-search.search", name = "mode", havingValue = "hybrid", matchIfMissing = true)
public class KnnSearchStrategy implements SearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(KnnSearchStrategy.class);

    private final ElasticsearchClient client;
    private final AiSearchProperties properties;
    private final QueryEmbeddingService queryEmbeddingService;
    private final SearchFilterQueryBuilder filterQueryBuilder;
    private final HybridBaseQueryBuilder hybridBaseQueryBuilder;
    private final ElasticsearchSearchRequestBuilder searchRequestBuilder;
    private final DefaultSearchResponseMapper searchResponseMapper;
    private final CategoryBoostingDecider categoryBoostingDecider;

    public KnnSearchStrategy(
            ElasticsearchClient client,
            AiSearchProperties properties,
            QueryEmbeddingService queryEmbeddingService,
            SearchFilterQueryBuilder filterQueryBuilder,
            HybridBaseQueryBuilder hybridBaseQueryBuilder,
            ElasticsearchSearchRequestBuilder searchRequestBuilder,
            DefaultSearchResponseMapper searchResponseMapper,
            CategoryBoostingDecider categoryBoostingDecider
    ) {
        this.client = client;
        this.properties = properties;
        this.queryEmbeddingService = queryEmbeddingService;
        this.filterQueryBuilder = filterQueryBuilder;
        this.hybridBaseQueryBuilder = hybridBaseQueryBuilder;
        this.searchRequestBuilder = searchRequestBuilder;
        this.searchResponseMapper = searchResponseMapper;
        this.categoryBoostingDecider = categoryBoostingDecider;
    }

    @Override
    public SearchPageResult search(ProductSearchRequest searchRequest, Pageable pageable) {
        try {
            // 검색어가 있으면 임베딩 + script_score 기반 하이브리드 검색을 수행한다.
            if (searchRequest.hasQuery()) {
                return hybridSearch(searchRequest, pageable);
            }
            // 검색어가 없으면 필터/정렬만 적용한 일반 검색을 수행한다.
            return filterOnlySearch(searchRequest, pageable);
        } catch (IOException e) {
            log.error("Search request failed. query={}, alias={}", searchRequest.query(), getReadAlias(), e);
            throw new IllegalStateException("검색 요청 실패: " + summarizeException(e), e);
        }
    }

    private SearchPageResult hybridSearch(ProductSearchRequest request, Pageable pageable) throws IOException {
        CategoryBoostingResult decision = categoryBoostingDecider.decide(request);
        try {
            // 사용자 질의를 임베딩 벡터로 변환해 cosineSimilarity 계산에 사용한다.
            Query baseQuery = hybridBaseQueryBuilder.build(request, filterQueryBuilder.buildFilterQuery(request));
            co.elastic.clients.elasticsearch.core.SearchRequest esSearchRequest = searchRequestBuilder.buildHybridRequest(
                    getReadAlias(),
                    baseQuery,
                    decision,
                    queryEmbeddingService.toQueryEmbedding(request.query()),
                    (int) pageable.getOffset(),
                    pageable.getPageSize()
            );

            SearchResponse<Map> response = client.search(esSearchRequest, Map.class);
            return searchResponseMapper.toPageResult(response, pageable);
        } catch (QueryEmbeddingUnavailableException e) {
            log.warn("Query embedding unavailable. Falling back to lexical search. query={}", request.query(), e);
            return lexicalFallbackSearch(request, pageable, decision);
        }
    }

    private SearchPageResult filterOnlySearch(
            ProductSearchRequest request,
            Pageable pageable
    ) throws IOException {
        co.elastic.clients.elasticsearch.core.SearchRequest esSearchRequest = searchRequestBuilder.buildFilterOnlyRequest(
                getReadAlias(),
                filterQueryBuilder.buildRootQuery(request),
                request.sortOption(),
                (int) pageable.getOffset(),
                pageable.getPageSize()
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
        co.elastic.clients.elasticsearch.core.SearchRequest esSearchRequest = searchRequestBuilder.buildFilterOnlyRequest(
                getReadAlias(),
                lexicalFallbackQuery,
                decision.searchSortOption(),
                (int) pageable.getOffset(),
                pageable.getPageSize()
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
