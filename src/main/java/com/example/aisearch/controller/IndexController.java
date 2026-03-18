package com.example.aisearch.controller;

import com.example.aisearch.controller.dto.RestoreIndexCandidatesResponseDto;
import com.example.aisearch.controller.dto.RestoreIndexRequestDto;
import com.example.aisearch.controller.dto.RestoreIndexResponseDto;
import com.example.aisearch.service.indexing.orchestration.IndexRestoreService;
import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexCandidatesResult;
import com.example.aisearch.service.indexing.orchestration.result.RestoreIndexResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {

    private final IndexRestoreService indexRestoreService;

    public IndexController(IndexRestoreService indexRestoreService) {
        this.indexRestoreService = indexRestoreService;
    }

    @GetMapping("/api/admin/index-restore/candidates")
    public RestoreIndexCandidatesResponseDto listRestoreCandidates() {
        RestoreIndexCandidatesResult result = indexRestoreService.listCandidates();
        return RestoreIndexCandidatesResponseDto.from(result);
    }

    @PostMapping("/api/admin/index-restore")
    public RestoreIndexResponseDto restoreIndex(
            @RequestBody RestoreIndexRequestDto requestDto
    ) {
        if (requestDto == null) {
            throw new IllegalArgumentException("request body가 비어 있습니다.");
        }
        RestoreIndexResult result = indexRestoreService.restoreTo(requestDto.targetIndex());
        return RestoreIndexResponseDto.from(result);
    }
}
