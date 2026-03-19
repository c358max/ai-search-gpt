package com.example.aisearch.service.feedback;

public record ModelFeedbackOverallSummary(
        String modelKey,
        double averageScore,
        long ratingCount
) {
}
