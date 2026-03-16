package com.example.aisearch.service.indexing.domain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.indexing.domain.exception.IndexCleanupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 롤아웃 후 불필요해진 버전 인덱스를 정리하는 도메인 서비스.
 */
@Service
public class IndexCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IndexCleanupService.class);

    private final ElasticsearchClient esClient;
    private final AiSearchProperties properties;

    public IndexCleanupService(ElasticsearchClient esClient, AiSearchProperties properties) {
        this.esClient = esClient;
        this.properties = properties;
    }

    /**
     * 현재 alias 대상 인덱스를 포함해 최신 N개만 남기고 나머지 버전 인덱스를 삭제한다.
     *
     * @param currentAliasedIndex 현재 read alias가 가리키는 인덱스(null 허용)
     * @return 정리 결과 요약
     */
    public IndexCleanupResult cleanupOldVersionedIndices(String currentAliasedIndex) {
        validateRetentionCount();

        try {
            List<String> versionedIndices = findVersionedIndices();
            List<String> deleteTargets = planDeleteTargets(versionedIndices, currentAliasedIndex);

            log.info("Index cleanup candidates. currentIndex={}, retentionCount={}, currentIndexCount={}, deleteTargets={}",
                    currentAliasedIndex, properties.indexRetentionCount(), versionedIndices.size(), deleteTargets);

            if (deleteTargets.isEmpty()) {
                log.info("Index cleanup skipped. currentIndexCount={}, deleteTargets=0",
                        versionedIndices.size());
                return new IndexCleanupResult(versionedIndices.size(), List.of());
            }

            List<String> deletedIndices = new ArrayList<>();
            for (String target : deleteTargets) {
                esClient.indices().delete(d -> d.index(target));
                deletedIndices.add(target);
            }

            log.info("Index cleanup complete. deletedCount={}, deletedIndices={}",
                    deletedIndices.size(), deletedIndices);
            return new IndexCleanupResult(versionedIndices.size(), List.copyOf(deletedIndices));
        } catch (IOException e) {
            throw new IndexCleanupException("버전 인덱스 정리 실패", e);
        }
    }

    List<String> planDeleteTargets(List<String> versionedIndices, String currentAliasedIndex) {
        validateRetentionCount();

        List<String> sortedVersioned = versionedIndices.stream()
                .filter(this::isVersionedIndex)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        if (sortedVersioned.size() <= properties.indexRetentionCount()) {
            return List.of();
        }

        Set<String> keep = new LinkedHashSet<>();
        if (currentAliasedIndex != null && isVersionedIndex(currentAliasedIndex) && sortedVersioned.contains(currentAliasedIndex)) {
            keep.add(currentAliasedIndex);
        }

        for (String index : sortedVersioned) {
            if (keep.size() >= properties.indexRetentionCount()) {
                break;
            }
            keep.add(index);
        }

        return sortedVersioned.stream()
                .filter(index -> !keep.contains(index))
                .toList();
    }

    private List<String> findVersionedIndices() throws IOException {
        String indexPattern = properties.indexName() + "-v*";
        boolean exists = esClient.indices().exists(e -> e.index(indexPattern)).value();
        if (!exists) {
            return List.of();
        }

        Map<String, ?> indexMap = esClient.indices()
                .get(g -> g.index(indexPattern).ignoreUnavailable(true).allowNoIndices(true))
                .result();

        return indexMap.keySet().stream()
                .filter(this::isVersionedIndex)
                .toList();
    }

    private boolean isVersionedIndex(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("^" + Pattern.quote(properties.indexName()) + "-v\\d{14}$");
        return pattern.matcher(indexName).matches();
    }

    private void validateRetentionCount() {
        if (properties.indexRetentionCount() < 2) {
            throw new IllegalStateException("ai-search.index-retention-count 값은 2 이상이어야 합니다.");
        }
    }

    public record IndexCleanupResult(int currentIndexCount, List<String> deletedIndices) {
    }
}
