package com.example.aisearch.service.indexing.orchestration;

public record RestoreIndexCandidate(
        String indexName,
        boolean current,
        boolean restorable
) {
}
