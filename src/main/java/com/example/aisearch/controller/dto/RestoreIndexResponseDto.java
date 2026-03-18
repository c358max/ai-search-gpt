package com.example.aisearch.controller.dto;

import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexResult;

public record RestoreIndexResponseDto(
        boolean success,
        String alias,
        String oldIndex,
        String restoredIndex
) {
    public static RestoreIndexResponseDto from(RestoreIndexResult result) {
        return new RestoreIndexResponseDto(
                result.success(),
                result.alias(),
                result.oldIndex(),
                result.restoredIndex()
        );
    }
}
