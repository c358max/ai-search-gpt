package com.example.aisearch.service.search.categoryboost.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonCategoryBoostRulesTest {

    @Test
    @DisplayName("V1 픽스처에서 카테고리 부스팅 룰을 로드한다")
    void shouldLoadRulesFromFixtureV1() {
        JsonCategoryBoostRules rules = new JsonCategoryBoostRules(
                new DefaultResourceLoader(),
                new ObjectMapper(),
                "classpath:data/category_boosting_v1.json",
                300
        );

        Optional<Map<String, Double>> apple = rules.findByKeyword("사과");
        assertTrue(apple.isPresent());
        assertEquals(0.20, apple.get().get("4"));
    }

    @Test
    @DisplayName("룰 파일 경로를 바꾸고 다시 로드하면 새 버전을 반영한다")
    void shouldReloadRulesWhenVersionChangesByPathSwitching() {
        JsonCategoryBoostRules rules = new JsonCategoryBoostRules(
                new DefaultResourceLoader(),
                new ObjectMapper(),
                "classpath:data/category_boosting_v1.json",
                300
        );

        assertEquals(0.20, rules.findByKeyword("사과").orElseThrow().get("4"));

        rules.setRuleFilePath("classpath:data/category_boosting_v2.json");
        rules.reload();

        assertEquals(0.30, rules.findByKeyword("사과").orElseThrow().get("4"));
    }

    @Test
    @DisplayName("배열 기반 category boost JSON도 정상적으로 로드한다")
    void shouldLoadRulesFromArrayBasedCategoryBoostJson() {
        JsonCategoryBoostRules rules = new JsonCategoryBoostRules(
                new DefaultResourceLoader(),
                new ObjectMapper(),
                "classpath:data/category_boost.json",
                300
        );

        Map<String, Double> 건강 = rules.findByKeyword("건강").orElseThrow();
        assertEquals(0.9, 건강.get("5102"));
        assertEquals(0.9, 건강.get("5108"));
        assertEquals(0.9, 건강.get("5110"));

        Map<String, Double> 간장 = rules.findByKeyword("간장").orElseThrow();
        assertEquals(0.3, 간장.get("5081"));
    }
}
