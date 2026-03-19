package com.example.aisearch.controller.dto;

import com.example.aisearch.service.feedback.ModelFeedbackOverallSummary;

public record ModelFeedbackOverallResponseDto(
        String modelKey,
        double averageScore,
        long ratingCount
) {
    public static ModelFeedbackOverallResponseDto from(ModelFeedbackOverallSummary summary) {
        return new ModelFeedbackOverallResponseDto(
                summary.modelKey(),
                summary.averageScore(),
                summary.ratingCount()
        );
    }
}
