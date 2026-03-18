package com.example.aisearch.support;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.example.aisearch.model.search.ProductSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonGenerator;

import java.io.StringWriter;

/**
 * request/query 디버깅 출력을 한 곳에 모으기 위한 공용 유틸입니다.
 */
public final class SearchDebugPrintSupport {

    private SearchDebugPrintSupport() {
    }

    public static void printRequest(String label, ProductSearchRequest request) {
        System.out.println("[REQUEST] " + label + "=" + request);
    }

    public static void printSearchRequest(String label, SearchRequest request) throws Exception {
        System.out.println("=== " + label + " ===");
        System.out.println(formatSearchRequestForConsole(request));
    }

    public static String toPrettyJson(SearchRequest request) throws Exception {
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        StringWriter writer = new StringWriter();
        JsonGenerator generator = mapper.jsonProvider().createGenerator(writer);
        request.serialize(generator, mapper);
        generator.close();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readTree(writer.toString()));
    }

    public static String formatSearchRequestForConsole(SearchRequest request) throws Exception {
        String prettyJson = toPrettyJson(request);
        String withNewLines = prettyJson.replace("\\n", "\n");
        String scriptSource = extractScriptSource(withNewLines);
        if (scriptSource == null || scriptSource.isBlank()) {
            return withNewLines;
        }
        return withNewLines
                + "\n\n--- SCRIPT SOURCE (READABLE) ---\n"
                + indent(scriptSource, "  ");
    }

    private static String extractScriptSource(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode sourceNode = root.path("query")
                    .path("script_score")
                    .path("script")
                    .path("source");
            if (sourceNode.isMissingNode() || sourceNode.isNull()) {
                return null;
            }
            return sourceNode.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String indent(String text, String prefix) {
        return text.lines()
                .map(line -> prefix + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
