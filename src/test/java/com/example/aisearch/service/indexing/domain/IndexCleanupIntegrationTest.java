package com.example.aisearch.service.indexing.domain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.aisearch.TruststoreTestBase;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.indexing.orchestration.IndexRolloutResult;
import com.example.aisearch.service.indexing.orchestration.IndexRolloutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "ai-search.index-name=retention-it-products",
        "ai-search.read-alias=retention-it-products-read",
        "ai-search.synonyms-set=retention-it-synonyms",
        "ai-search.index-retention-count=3"
})
class IndexCleanupIntegrationTest extends TruststoreTestBase {

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private AiSearchProperties properties;

    @Autowired
    private AliasSwitcher aliasSwitcher;

    @Autowired
    private IndexRolloutService indexRolloutService;

    @BeforeEach
    void setUp() throws IOException {
        deleteAllVersionedIndices();
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteAllVersionedIndices();
    }

    @Test
    void retentionCleanup은_실제_rollout_4회후_최신3개만_남기고_oldest를_삭제한다() throws Exception {
        IndexRolloutResult first = indexRolloutService.rollOutFromSourceData();
        System.out.println("[ROLLOUT-1] oldIndex=" + first.oldIndex() + ", newIndex=" + first.newIndex() + ", indexedCount=" + first.indexedCount());
        Thread.sleep(1100L);
        IndexRolloutResult second = indexRolloutService.rollOutFromSourceData();
        System.out.println("[ROLLOUT-2] oldIndex=" + second.oldIndex() + ", newIndex=" + second.newIndex() + ", indexedCount=" + second.indexedCount());
        Thread.sleep(1100L);
        IndexRolloutResult third = indexRolloutService.rollOutFromSourceData();
        System.out.println("[ROLLOUT-3] oldIndex=" + third.oldIndex() + ", newIndex=" + third.newIndex() + ", indexedCount=" + third.indexedCount());
        Thread.sleep(1100L);
        IndexRolloutResult fourth = indexRolloutService.rollOutFromSourceData();
        System.out.println("[ROLLOUT-4] oldIndex=" + fourth.oldIndex() + ", newIndex=" + fourth.newIndex() + ", indexedCount=" + fourth.indexedCount());

        List<String> indicesBeforeCleanupExpectation = List.of(
                first.newIndex(),
                second.newIndex(),
                third.newIndex(),
                fourth.newIndex()
        );
        System.out.println("[BEFORE-CHECK] rolloutGeneratedIndices=" + indicesBeforeCleanupExpectation);
        System.out.println("[BEFORE-CHECK] rolloutGeneratedIndexCount=" + indicesBeforeCleanupExpectation.size());

        List<String> remainingIndices = findVersionedIndices();
        System.out.println("[AFTER-CLEANUP] remainingIndices=" + remainingIndices);
        System.out.println("[AFTER-CLEANUP] remainingIndexCount=" + remainingIndices.size());
        System.out.println("[AFTER-CLEANUP] deletedOldest=" + first.newIndex());
        System.out.println("[AFTER-CLEANUP] currentAliasIndex=" + aliasSwitcher.findCurrentAliasedIndex());

        assertEquals(fourth.newIndex(), aliasSwitcher.findCurrentAliasedIndex());
        assertEquals(3, remainingIndices.size());
        assertTrue(remainingIndices.contains(second.newIndex()));
        assertTrue(remainingIndices.contains(third.newIndex()));
        assertTrue(remainingIndices.contains(fourth.newIndex()));
        assertFalse(remainingIndices.contains(first.newIndex()));
        assertFalse(indexExists(first.newIndex()));
    }

    private List<String> findVersionedIndices() throws IOException {
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

    private boolean indexExists(String indexName) throws IOException {
        return esClient.indices().exists(e -> e.index(indexName)).value();
    }

    private void deleteAllVersionedIndices() throws IOException {
        String pattern = properties.indexName() + "-v*";
        boolean exists = esClient.indices().exists(e -> e.index(pattern)).value();
        if (!exists) {
            return;
        }

        Map<String, ?> indices = esClient.indices()
                .get(g -> g.index(pattern).ignoreUnavailable(true).allowNoIndices(true))
                .result();

        for (String index : indices.keySet()) {
            esClient.indices().delete(d -> d.index(index));
        }
    }
}
