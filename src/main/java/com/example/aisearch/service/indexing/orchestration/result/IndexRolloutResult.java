package com.example.aisearch.service.indexing.orchestration.result;

public record IndexRolloutResult(
        String oldIndex,
        String newIndex,
        long indexedCount
) {
}
