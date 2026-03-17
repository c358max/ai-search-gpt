package com.example.aisearch.service.indexing.orchestration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.indexing.domain.AliasSwitcher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class IndexRestoreService {

    private final ElasticsearchClient esClient;
    private final AiSearchProperties properties;
    private final AliasSwitcher aliasSwitcher;

    public IndexRestoreService(
            ElasticsearchClient esClient,
            AiSearchProperties properties,
            AliasSwitcher aliasSwitcher
    ) {
        this.esClient = esClient;
        this.properties = properties;
        this.aliasSwitcher = aliasSwitcher;
    }

    public RestoreIndexCandidatesResult listCandidates() {
        String currentAliasIndex = aliasSwitcher.findCurrentAliasedIndex();
        List<RestoreIndexCandidate> candidates = findVersionedIndices().stream()
                .map(index -> new RestoreIndexCandidate(
                        index,
                        index.equals(currentAliasIndex),
                        !index.equals(currentAliasIndex)
                ))
                .toList();

        return new RestoreIndexCandidatesResult(
                properties.readAlias(),
                currentAliasIndex,
                properties.indexRetentionCount(),
                candidates.size(),
                candidates
        );
    }

    public RestoreIndexResult restoreTo(String targetIndex) {
        validateTargetIndex(targetIndex);

        String currentAliasIndex = aliasSwitcher.findCurrentAliasedIndex();
        if (targetIndex.equals(currentAliasIndex)) {
            throw new IllegalArgumentException("targetIndex가 현재 alias 대상 인덱스와 동일합니다.");
        }
        if (!indexExists(targetIndex)) {
            throw new IllegalArgumentException("targetIndex가 존재하지 않습니다. targetIndex=" + targetIndex);
        }

        aliasSwitcher.swapReadAlias(currentAliasIndex, targetIndex);

        String restoredAliasIndex = aliasSwitcher.findCurrentAliasedIndex();
        if (!targetIndex.equals(restoredAliasIndex)) {
            throw new IllegalStateException("restore 결과 검증 실패. expected=" + targetIndex + ", actual=" + restoredAliasIndex);
        }

        return new RestoreIndexResult(true, properties.readAlias(), currentAliasIndex, targetIndex);
    }

    private List<String> findVersionedIndices() {
        String pattern = properties.indexName() + "-v*";
        try {
            boolean exists = esClient.indices().exists(e -> e.index(pattern)).value();
            if (!exists) {
                return List.of();
            }

            Map<String, ?> indexMap = esClient.indices()
                    .get(g -> g.index(pattern).ignoreUnavailable(true).allowNoIndices(true))
                    .result();

            return indexMap.keySet().stream()
                    .filter(this::isVersionedIndex)
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("복구 후보 인덱스 조회 실패", e);
        }
    }

    private boolean indexExists(String indexName) {
        try {
            return esClient.indices().exists(e -> e.index(indexName)).value();
        } catch (IOException e) {
            throw new IllegalStateException("인덱스 존재 여부 조회 실패. index=" + indexName, e);
        }
    }

    private void validateTargetIndex(String targetIndex) {
        if (targetIndex == null || targetIndex.isBlank()) {
            throw new IllegalArgumentException("targetIndex는 비어 있을 수 없습니다.");
        }
        if (!isVersionedIndex(targetIndex)) {
            throw new IllegalArgumentException("targetIndex 형식이 올바르지 않습니다. targetIndex=" + targetIndex);
        }
    }

    private boolean isVersionedIndex(String indexName) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(properties.indexName()) + "-v\\d{14}$");
        return pattern.matcher(indexName).matches();
    }
}
