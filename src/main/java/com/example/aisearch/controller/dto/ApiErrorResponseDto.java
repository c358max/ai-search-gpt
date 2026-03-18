package com.example.aisearch.controller.dto;

public record ApiErrorResponseDto(
        String code,
        String message
) {
}
