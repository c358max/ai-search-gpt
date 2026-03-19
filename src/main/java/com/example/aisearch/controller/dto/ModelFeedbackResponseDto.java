package com.example.aisearch.controller.dto;

import com.example.aisearch.service.feedback.ModelFeedbackSummary;

public record ModelFeedbackResponseDto(
        String modelKey,
        String query,
        int savedScore,
        double averageScore,
        long ratingCount
) {
    public static ModelFeedbackResponseDto from(ModelFeedbackSummary summary, int savedScore) {
        return new ModelFeedbackResponseDto(
                summary.modelKey(),
                summary.query(),
                savedScore,
                summary.averageScore(),
                summary.ratingCount()
        );
    }
}
