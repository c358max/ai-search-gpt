package com.example.aisearch.service.indexing.domain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.aisearch.config.AiSearchProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class VersionedIndexLocator {

    private final ElasticsearchClient esClient;
    private final AiSearchProperties properties;

    public VersionedIndexLocator(ElasticsearchClient esClient, AiSearchProperties properties) {
        this.esClient = esClient;
        this.properties = properties;
    }

    /**
     * 현재 base index 이름(properties.indexName)을 기준으로
     * Elasticsearch에 존재하는 버전 인덱스 목록을 모두 조회합니다.
     *
     * 예:
     * - base index: food-products
     * - 조회 패턴: food-products-v*
     *
     * 반환값은 최신 인덱스가 앞에 오도록 역순 정렬합니다.
     */
    public List<String> findVersionedIndices() throws IOException {
        String indexPattern = properties.indexName() + "-v*";
        boolean exists = esClient.indices().exists(e -> e.index(indexPattern)).value();
        if (!exists) {
            return List.of();
        }

        Map<String, ?> indexMap = esClient.indices()
                .get(g -> g.index(indexPattern).ignoreUnavailable(true).allowNoIndices(true))
                .result();

        return indexMap.keySet().stream()
                .filter(this::matchesVersionedIndexName)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * 특정 인덱스가 실제로 Elasticsearch에 존재하는지 확인합니다.
     *
     * 복구 대상 검증처럼 "버전 형식은 맞지만 실제로 없는 인덱스"를 걸러낼 때 사용합니다.
     */
    public boolean indexExists(String indexName) throws IOException {
        return esClient.indices().exists(e -> e.index(indexName)).value();
    }

    /**
     * 인덱스 이름이 현재 프로젝트의 버전 인덱스 규칙을 따르는지 판별합니다.
     *
     * 규칙:
     * - {baseIndexName}-vyyyyMMddHHmmss
     * - 예: food-products-v20260317113045
     *
     * 이 메서드는 "이름 형식이 맞는지"만 검사하며,
     * 실제 Elasticsearch에 존재하는지까지는 확인하지 않습니다.
     */
    public boolean matchesVersionedIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("^" + Pattern.quote(properties.indexName()) + "-v\\d{14}$");
        return pattern.matcher(indexName).matches();
    }
}
