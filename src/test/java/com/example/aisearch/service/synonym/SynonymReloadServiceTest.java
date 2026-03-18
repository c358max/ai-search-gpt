package com.example.aisearch.service.synonym;

import com.example.aisearch.config.AiSearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
