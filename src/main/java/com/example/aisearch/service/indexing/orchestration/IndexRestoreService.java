package com.example.aisearch.service.indexing.orchestration;

import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.indexing.domain.AliasSwitcher;
import com.example.aisearch.service.indexing.domain.VersionedIndexLocator;
import com.example.aisearch.service.indexing.orchestration.exception.InvalidRestoreTargetException;
import com.example.aisearch.service.indexing.orchestration.exception.RestoreTargetNotFoundException;
import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexCandidate;
import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexCandidatesResult;
import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class IndexRestoreService {

    private final AiSearchProperties properties;
    private final AliasSwitcher aliasSwitcher;
    private final VersionedIndexLocator versionedIndexLocator;

    public IndexRestoreService(
            AiSearchProperties properties,
            AliasSwitcher aliasSwitcher,
            VersionedIndexLocator versionedIndexLocator
    ) {
        this.properties = properties;
        this.aliasSwitcher = aliasSwitcher;
        this.versionedIndexLocator = versionedIndexLocator;
    }

    public RestoreIndexCandidatesResult listCandidates() {
        String currentAliasIndex = aliasSwitcher.findCurrentAliasedIndex();
        List<RestoreIndexCandidate> candidates;
        try {
            candidates = versionedIndexLocator.findVersionedIndices().stream()
                    .map(index -> new RestoreIndexCandidate(
                            index,
                            index.equals(currentAliasIndex),
                            !index.equals(currentAliasIndex)
                    ))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("복구 후보 인덱스 조회 실패", e);
        }

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
            throw new InvalidRestoreTargetException("targetIndex가 현재 alias 대상 인덱스와 동일합니다.");
        }
        try {
            if (!versionedIndexLocator.indexExists(targetIndex)) {
                throw new RestoreTargetNotFoundException("targetIndex가 존재하지 않습니다. targetIndex=" + targetIndex);
            }
        } catch (IOException e) {
            throw new IllegalStateException("인덱스 존재 여부 조회 실패. index=" + targetIndex, e);
        }

        aliasSwitcher.swapReadAlias(currentAliasIndex, targetIndex);

        String restoredAliasIndex = aliasSwitcher.findCurrentAliasedIndex();
        if (!targetIndex.equals(restoredAliasIndex)) {
            throw new IllegalStateException("restore 결과 검증 실패. expected=" + targetIndex + ", actual=" + restoredAliasIndex);
        }

        return new RestoreIndexResult(true, properties.readAlias(), currentAliasIndex, targetIndex);
    }

    private void validateTargetIndex(String targetIndex) {
        if (targetIndex == null || targetIndex.isBlank()) {
            throw new InvalidRestoreTargetException("targetIndex는 비어 있을 수 없습니다.");
        }
        if (!versionedIndexLocator.matchesVersionedIndexName(targetIndex)) {
            throw new InvalidRestoreTargetException("targetIndex 형식이 올바르지 않습니다. targetIndex=" + targetIndex);
        }
    }
}
