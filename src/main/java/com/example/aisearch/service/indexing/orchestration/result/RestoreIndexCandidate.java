package com.example.aisearch.service.indexing.orchestration.result;

public record RestoreIndexCandidate(
        String indexName,
        boolean current,
        boolean restorable
) {
}
