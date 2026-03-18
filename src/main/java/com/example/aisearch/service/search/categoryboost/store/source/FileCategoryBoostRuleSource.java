package com.example.aisearch.service.search.categoryboost.store.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * classpath/file 리소스에 저장된 category_boosting.json을 읽는 구현체입니다.
 *
 * 책임:
 * - 파일 열기
 * - JSON 파싱
 * - 룰 정규화
 *
 * 비책임:
 * - 메모리 캐시
 * - TTL gate
 * - reload orchestration
 */
@Component
public class FileCategoryBoostRuleSource implements CategoryBoostRuleSource {

    public static final String DEFAULT_RULE_FILE_PATH = "classpath:data/category_boost.json";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private volatile String ruleFilePath;

    @Autowired
    public FileCategoryBoostRuleSource(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this(resourceLoader, objectMapper, DEFAULT_RULE_FILE_PATH);
    }

    public FileCategoryBoostRuleSource(
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            String ruleFilePath
    ) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.ruleFilePath = ruleFilePath;
    }

    @Override
    public String readVersion() throws IOException {
        Resource resource = resourceLoader.getResource(currentRulePath());
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            return parseConfig(root, currentRulePath()).version();
        }
    }

    @Override
    public CategoryBoostRuleSnapshot loadSnapshot() throws IOException {
        Resource resource = resourceLoader.getResource(currentRulePath());
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            ParsedCategoryBoostConfig config = parseConfig(root, currentRulePath());
            return new CategoryBoostRuleSnapshot(config.version(), toRuleMap(config.rules()));
        }
    }

    @Override
    public String description() {
        return currentRulePath();
    }

    // 테스트/수동 제어에서만 경로를 바꿉니다.
    // store 패키지의 coordinator가 파일 source를 제어할 때만 사용합니다.
    public void setRuleFilePath(String ruleFilePath) {
        this.ruleFilePath = ruleFilePath;
    }

    private Map<String, Map<String, Double>> toRuleMap(List<CategoryBoostingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Double>> ruleMap = new LinkedHashMap<>();
        for (CategoryBoostingRule rule : rules) {
            if (rule == null || rule.keyword() == null) {
                continue;
            }
            String keyword = rule.keyword().trim();
            if (keyword.isBlank()) {
                continue;
            }
            Map<String, Double> boosts = normalizeBoostMap(rule.categoryBoostById(), rule.categoryId(), rule.score());
            if (boosts.isEmpty()) {
                continue;
            }
            ruleMap.merge(keyword, boosts, FileCategoryBoostRuleSource::mergeBoostMaps);
        }
        return Map.copyOf(ruleMap);
    }

    private static Map<String, Double> mergeBoostMaps(Map<String, Double> left, Map<String, Double> right) {
        Map<String, Double> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return Map.copyOf(merged);
    }

    private Map<String, Double> normalizeBoostMap(Map<String, Double> raw, String categoryId, Double score) {
        if (raw != null && !raw.isEmpty()) {
            return normalizeCategoryBoostById(raw);
        }

        if (categoryId == null || categoryId.isBlank() || score == null) {
            return Map.of();
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        normalized.put(categoryId.trim(), normalizeScore(score));
        return Map.copyOf(normalized);
    }

    private Map<String, Double> normalizeCategoryBoostById(Map<String, Double> raw) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            normalized.put(key.trim(), normalizeScore(value));
        }
        return Map.copyOf(normalized);
    }

    private double normalizeScore(Double rawScore) {
        if (rawScore == null) {
            throw new IllegalArgumentException("카테고리 부스팅 score 값이 비어 있습니다.");
        }
        return rawScore > 1.0 ? rawScore / 10.0 : rawScore;
    }

    private ParsedCategoryBoostConfig parseConfig(JsonNode root, String path) {
        if (root == null || root.isNull()) {
            throw new IllegalStateException("category_boost.json 내용이 비어 있습니다. path=" + path);
        }

        if (root.isArray()) {
            List<CategoryBoostingRule> rules = new ArrayList<>();
            for (JsonNode item : root) {
                rules.add(objectMapper.convertValue(item, CategoryBoostingRule.class));
            }
            return new ParsedCategoryBoostConfig(buildArrayVersion(root), rules);
        }

        if (root.isObject()) {
            CategoryBoostingConfig config = objectMapper.convertValue(root, CategoryBoostingConfig.class);
            if (config == null || config.version() == null || config.version().isBlank()) {
                throw new IllegalStateException("category_boost.json version 값이 유효하지 않습니다. path=" + path);
            }
            return new ParsedCategoryBoostConfig(config.version().trim(), config.rules());
        }

        throw new IllegalStateException("category_boost.json 형식이 올바르지 않습니다. path=" + path);
    }

    private String buildArrayVersion(JsonNode root) {
        return Integer.toHexString(root.toString().hashCode());
    }

    private String currentRulePath() {
        String path = ruleFilePath;
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("카테고리 부스팅 룰 파일 경로가 비어 있습니다.");
        }
        return path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CategoryBoostingConfig(
            String version,
            List<CategoryBoostingRule> rules
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CategoryBoostingRule(
            String keyword,
            Map<String, Double> categoryBoostById,
            @JsonProperty("category_id")
            String categoryId,
            @JsonProperty("score")
            Double score
    ) {
    }

    private record ParsedCategoryBoostConfig(
            String version,
            List<CategoryBoostingRule> rules
    ) {
    }
}
