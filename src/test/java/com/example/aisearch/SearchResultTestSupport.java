package com.example.aisearch;

import com.example.aisearch.model.SearchHitResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class SearchResultTestSupport {

    private SearchResultTestSupport() {
    }

    public static Integer asInteger(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    public static List<String> extractIds(List<SearchHitResult> results) {
        return results.stream().map(SearchHitResult::id).toList();
    }

    public static List<Integer> extractIntegers(List<SearchHitResult> results, String key) {
        return results.stream()
                .map(hit -> asInteger(hit.source(), key))
                .filter(value -> value != null)
                .toList();
    }

    public static boolean containsProductName(JsonNode results, String keyword) {
        for (JsonNode hit : results) {
            String name = hit.path("source").path("product_name").asText("");
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
