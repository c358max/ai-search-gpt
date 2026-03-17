package com.example.aisearch.service.indexing.orchestration;

public record RestoreIndexResult(
        boolean success,
        String alias,
        String oldIndex,
        String restoredIndex
) {
}
