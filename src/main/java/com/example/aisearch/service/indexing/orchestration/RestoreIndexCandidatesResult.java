package com.example.aisearch.service.indexing.orchestration;

import java.util.List;

public record RestoreIndexCandidatesResult(
        String alias,
        String currentAliasIndex,
        int retentionCount,
        int count,
        List<RestoreIndexCandidate> candidates
) {
}
