package com.example.aisearch;

import com.example.aisearch.model.SearchHitResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
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
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Number number) {
                return number.intValue();
            }
            if (first instanceof String text) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static Integer asCategoryInteger(Map<String, Object> source) {
        Integer primary = asInteger(source, "primary_lev3_category_id");
        return primary != null ? primary : asInteger(source, "lev3_category_id");
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
            JsonNode source = hit.path("source");
            String name = source.path("goods_name").asText(source.path("product_name").asText(""));
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
