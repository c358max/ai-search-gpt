package com.example.aisearch.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ModelFeedbackRequestDto(
        @NotBlank(message = "query는 비어 있을 수 없습니다.")
        String query,
        @Min(value = 1, message = "score는 1 이상이어야 합니다.")
        @Max(value = 5, message = "score는 5 이하여야 합니다.")
        Integer score
) {
}
