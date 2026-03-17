package com.example.aisearch.controller.dto;

import com.example.aisearch.service.indexing.orchestration.RestoreIndexCandidate;

public record RestoreIndexCandidateDto(
        String indexName,
        boolean current,
        boolean restorable
) {
    public static RestoreIndexCandidateDto from(RestoreIndexCandidate candidate) {
        return new RestoreIndexCandidateDto(
                candidate.indexName(),
                candidate.current(),
                candidate.restorable()
        );
    }
}
