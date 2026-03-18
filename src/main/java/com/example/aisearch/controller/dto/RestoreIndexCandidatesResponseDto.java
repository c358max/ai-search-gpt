package com.example.aisearch.controller.dto;

import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexCandidatesResult;

import java.util.List;

public record RestoreIndexCandidatesResponseDto(
        String alias,
        String currentAliasIndex,
        int retentionCount,
        int count,
        List<RestoreIndexCandidateDto> candidates
) {
    public static RestoreIndexCandidatesResponseDto from(RestoreIndexCandidatesResult result) {
        return new RestoreIndexCandidatesResponseDto(
                result.alias(),
                result.currentAliasIndex(),
                result.retentionCount(),
                result.count(),
                result.candidates().stream()
                        .map(RestoreIndexCandidateDto::from)
                        .toList()
        );
    }
}
