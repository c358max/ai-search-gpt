package com.example.aisearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.aisearch.config.AiSearchProperties;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public abstract class ElasticsearchIntegrationTestBase extends TruststoreTestBase {

    @Autowired
    protected ElasticsearchClient esClient;

    @Autowired
    protected AiSearchProperties properties;

    protected void printIsolationConfig(String testName) {
        System.out.println("[TEST-CONFIG] test=" + testName
                + ", indexName=" + properties.indexName()
                + ", readAlias=" + properties.readAlias()
                + ", synonymsSet=" + properties.synonymsSet());
    }

    protected void deleteAllVersionedIndices() throws IOException {
        for (String index : findVersionedIndices()) {
            esClient.indices().delete(d -> d.index(index));
        }
    }

    protected List<String> findVersionedIndices() throws IOException {
        String pattern = properties.indexName() + "-v*";
        boolean exists = esClient.indices().exists(e -> e.index(pattern)).value();
        if (!exists) {
            return List.of();
        }

        Map<String, ?> indexMap = esClient.indices()
                .get(g -> g.index(pattern).ignoreUnavailable(true).allowNoIndices(true))
                .result();

        return indexMap.keySet().stream()
                .filter(index -> index.startsWith(properties.indexName() + "-v"))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    protected boolean indexExists(String indexName) throws IOException {
        return esClient.indices().exists(e -> e.index(indexName)).value();
    }
}
