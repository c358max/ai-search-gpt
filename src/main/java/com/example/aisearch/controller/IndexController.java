package com.example.aisearch.controller;

import com.example.aisearch.controller.dto.RestoreIndexCandidatesResponseDto;
import com.example.aisearch.controller.dto.RestoreIndexRequestDto;
import com.example.aisearch.controller.dto.RestoreIndexResponseDto;
import com.example.aisearch.service.indexing.orchestration.IndexRestoreService;
import com.example.aisearch.service.indexing.orchestration.RestoreIndexCandidatesResult;
import com.example.aisearch.service.indexing.orchestration.RestoreIndexResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IndexController {

    private final IndexRestoreService indexRestoreService;

    public IndexController(IndexRestoreService indexRestoreService) {
        this.indexRestoreService = indexRestoreService;
    }

    @GetMapping("/api/admin/index-restore/candidates")
    public RestoreIndexCandidatesResponseDto listRestoreCandidates() {
        try {
            RestoreIndexCandidatesResult result = indexRestoreService.listCandidates();
            return RestoreIndexCandidatesResponseDto.from(result);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @PostMapping("/api/admin/index-restore")
    public RestoreIndexResponseDto restoreIndex(
            @RequestBody RestoreIndexRequestDto requestDto
    ) {
        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body가 비어 있습니다.");
        }
        try {
            RestoreIndexResult result = indexRestoreService.restoreTo(requestDto.targetIndex());
            return RestoreIndexResponseDto.from(result);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("존재하지 않습니다")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }
}
