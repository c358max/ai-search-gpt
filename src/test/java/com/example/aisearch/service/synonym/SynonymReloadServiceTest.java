package com.example.aisearch.service.synonym;

import com.example.aisearch.config.AiSearchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SynonymReloadServiceTest {

    @Mock
    private SynonymRuleSource synonymRuleSource;

    @Mock
    private SynonymEsGateway synonymEsGateway;

    @Test
    @DisplayName("동의어 재로드는 항상 read alias를 대상으로 수행한다")
    void reload는_항상_readAlias를_대상으로_사용한다() {
        AiSearchProperties properties = properties("food-products-read-test");
        when(synonymRuleSource.loadRules(SynonymReloadMode.PRODUCTION))
                .thenReturn(List.of("교자,만두"));

        SynonymReloadService service = new SynonymReloadService(properties, synonymRuleSource, synonymEsGateway);
        SynonymReloadResult result = service.reload(SynonymReloadRequest.defaultRequest());

        verify(synonymEsGateway).reloadSearchAnalyzers("food-products-read-test");
        assertEquals("food-products-read-test", result.index());
    }

    @Test
    @DisplayName("read alias가 비어 있으면 동의어 재로드를 실패시킨다")
    void readAlias가_비어있으면_reload는_실패한다() {
        AiSearchProperties properties = properties(" ");
        SynonymReloadService service = new SynonymReloadService(properties, synonymRuleSource, synonymEsGateway);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.reload(SynonymReloadRequest.defaultRequest())
        );

        assertEquals("ai-search.read-alias 값이 비어 있습니다.", ex.getMessage());
        verifyNoInteractions(synonymRuleSource, synonymEsGateway);
    }

    @Test
    @DisplayName("운영 동의어 세트 조회가 일시적으로 실패하면 재시도 후 기존 세트를 재사용한다")
    void ensureProductionSynonymsSet은_일시적_조회_실패를_재시도한다() {
        AiSearchProperties properties = properties("food-products-read-test");
        SynonymReloadService service = new SynonymReloadService(properties, synonymRuleSource, synonymEsGateway);

        when(synonymEsGateway.existsSynonyms("food-synonyms"))
                .thenThrow(new IllegalStateException("동의어 세트 조회 실패: food-synonyms | status: 503"))
                .thenThrow(new IllegalStateException("동의어 세트 조회 실패: food-synonyms | no_shard_available_action_exception"))
                .thenReturn(true);

        service.ensureProductionSynonymsSet();

        verify(synonymEsGateway, times(3)).existsSynonyms("food-synonyms");
        verifyNoInteractions(synonymRuleSource);
        verify(synonymEsGateway, times(0)).putSynonyms(eq("food-synonyms"), anyList());
    }

    @Test
    @DisplayName("운영 동의어 세트가 없고 반영이 일시적으로 실패해도 재시도 후 생성한다")
    void ensureProductionSynonymsSet은_put실패도_재시도한다() {
        AiSearchProperties properties = properties("food-products-read-test");
        SynonymReloadService service = new SynonymReloadService(properties, synonymRuleSource, synonymEsGateway);

        when(synonymEsGateway.existsSynonyms("food-synonyms")).thenReturn(false);
        when(synonymRuleSource.loadRules(SynonymReloadMode.PRODUCTION))
                .thenReturn(List.of("교자,만두"));
        org.mockito.Mockito.doThrow(new IllegalStateException("동의어 세트 반영 실패: food-synonyms | status: 503"))
                .doThrow(new IllegalStateException("동의어 세트 반영 실패: food-synonyms | .synonyms-2"))
                .doNothing()
                .when(synonymEsGateway)
                .putSynonyms(eq("food-synonyms"), eq(List.of("교자,만두")));

        service.ensureProductionSynonymsSet();

        verify(synonymEsGateway).existsSynonyms("food-synonyms");
        verify(synonymRuleSource).loadRules(SynonymReloadMode.PRODUCTION);
        verify(synonymEsGateway, times(3)).putSynonyms("food-synonyms", List.of("교자,만두"));
    }

    private AiSearchProperties properties(String readAlias) {
        return new AiSearchProperties(
                "http://localhost:9200",
                "elastic",
                "password",
                "food-products",
                readAlias,
                "food-synonyms",
                "classpath:es/dictionary/synonyms_ko.txt",
                "classpath:es/dictionary/synonyms_kr_regression.txt",
                "djl://example",
                "classpath:/model",
                0.74,
                300L,
                300L,
                5000L,
                1500L,
                2,
                3
        );
    }
}
