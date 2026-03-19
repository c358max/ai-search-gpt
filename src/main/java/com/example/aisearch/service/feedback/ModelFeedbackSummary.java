package com.example.aisearch.service.feedback;

public record ModelFeedbackSummary(
        String modelKey,
        String query,
        double averageScore,
        long ratingCount
) {
}
