package com.example.aisearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class RestApiIntegrationTestBase extends ElasticsearchIntegrationTestBase {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected abstract int port();

    protected HttpResponse<String> get(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiUri(pathAndQuery)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiUri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected JsonNode readJson(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse response body", e);
        }
    }

    protected JsonNode getJsonAndAssertOk(String pathAndQuery) throws IOException, InterruptedException {
        HttpResponse<String> response = get(pathAndQuery);
        assertEquals(200, response.statusCode());
        return readJson(response);
    }

    protected JsonNode postJsonAndAssertOk(String path, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = postJson(path, body);
        assertEquals(200, response.statusCode());
        return readJson(response);
    }

    private URI apiUri(String pathAndQuery) {
        return URI.create("http://localhost:" + port() + pathAndQuery);
    }
}
