package com.example.aisearch.service.feedback;

import com.example.aisearch.config.AiSearchProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class ModelFeedbackService {

    private final AiSearchProperties properties;
    private final ModelFeedbackRepository repository;

    public ModelFeedbackService(AiSearchProperties properties, ModelFeedbackRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public ModelFeedbackSummary save(String query, int score) {
        String normalizedQuery = normalizeQuery(query);
        validateScore(score);

        repository.save(properties.indexName(), normalizedQuery, score);
        return getSummary(normalizedQuery);
    }

    public ModelFeedbackSummary getSummary(String query) {
        String normalizedQuery = normalizeQuery(query);
        try {
            return repository.summarize(properties.indexName(), normalizedQuery);
        } catch (EmptyResultDataAccessException e) {
            return new ModelFeedbackSummary(properties.indexName(), normalizedQuery, 0.0, 0);
        }
    }

    public ModelFeedbackOverallSummary getOverallSummary() {
        try {
            return repository.summarizeOverall(properties.indexName());
        } catch (EmptyResultDataAccessException e) {
            return new ModelFeedbackOverallSummary(properties.indexName(), 0.0, 0);
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query는 비어 있을 수 없습니다.");
        }
        return query.trim();
    }

    private void validateScore(int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("score는 1~5 범위여야 합니다.");
        }
    }
}
