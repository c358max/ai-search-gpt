package com.example.aisearch.service.feedback;

import com.example.aisearch.config.AiSearchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelFeedbackServiceTest {

    @Mock
    private ModelFeedbackRepository repository;

    @Test
    @DisplayName("평가 저장 후 같은 모델/검색어 기준 평균 정보를 반환한다")
    void shouldSaveFeedbackAndReturnSummary() {
        ModelFeedbackService service = new ModelFeedbackService(properties("food-products-bge-m3"), repository);
        when(repository.summarize("food-products-bge-m3", "어린이 간식"))
                .thenReturn(new ModelFeedbackSummary("food-products-bge-m3", "어린이 간식", 4.5, 2));

        ModelFeedbackSummary summary = service.save("  어린이 간식  ", 5);

        verify(repository).save("food-products-bge-m3", "어린이 간식", 5);
        assertEquals(4.5, summary.averageScore());
        assertEquals(2, summary.ratingCount());
    }

    @Test
    @DisplayName("저장된 평가가 없으면 평균 0, 건수 0으로 응답한다")
    void shouldReturnEmptySummaryWhenNoFeedbackExists() {
        ModelFeedbackService service = new ModelFeedbackService(properties("food-products-kure-v1"), repository);
        when(repository.summarize("food-products-kure-v1", "새우"))
                .thenThrow(new EmptyResultDataAccessException(1));

        ModelFeedbackSummary summary = service.getSummary("새우");

        assertEquals("food-products-kure-v1", summary.modelKey());
        assertEquals("새우", summary.query());
        assertEquals(0.0, summary.averageScore());
        assertEquals(0, summary.ratingCount());
    }

    @Test
    @DisplayName("모델별 전체 누적 평균과 건수를 반환한다")
    void shouldReturnOverallSummary() {
        ModelFeedbackService service = new ModelFeedbackService(properties("food-products-bge-m3"), repository);
        when(repository.summarizeOverall("food-products-bge-m3"))
                .thenReturn(new ModelFeedbackOverallSummary("food-products-bge-m3", 3.8, 12));

        ModelFeedbackOverallSummary summary = service.getOverallSummary();

        assertEquals("food-products-bge-m3", summary.modelKey());
        assertEquals(3.8, summary.averageScore());
        assertEquals(12, summary.ratingCount());
    }

    @Test
    @DisplayName("score가 1~5 범위를 벗어나면 저장을 거부한다")
    void shouldRejectOutOfRangeScore() {
        ModelFeedbackService service = new ModelFeedbackService(properties("food-products-e5-small-ko-v2"), repository);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.save("간식", 6));
        assertEquals("score는 1~5 범위여야 합니다.", ex.getMessage());
    }

    @Test
    @DisplayName("query가 비어 있으면 조회를 거부한다")
    void shouldRejectBlankQuery() {
        ModelFeedbackService service = new ModelFeedbackService(properties("food-products-e5-small-ko-v2"), repository);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.getSummary("   "));
        assertEquals("query는 비어 있을 수 없습니다.", ex.getMessage());
    }

    private static AiSearchProperties properties(String indexName) {
        return new AiSearchProperties(
                "http://127.0.0.1:9210",
                "elastic",
                "elastic",
                indexName,
                indexName + "-read",
                "food-synonyms",
                "classpath:data/synonyms.txt",
                "classpath:data/synonyms-regression.txt",
                null,
                "classpath:/model/test-model",
                0.0,
                60,
                60,
                1000,
                5000,
                2,
                3
        );
    }
}
