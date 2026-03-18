package com.example.aisearch.service.indexing.bootstrap.ingest;

import com.example.aisearch.model.FoodProduct;
import com.example.aisearch.service.embedding.EmbeddingInputFormatter;
import com.example.aisearch.service.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexingService.class);
    private static final int BULK_BATCH_SIZE = 100;

    private final EmbeddingService embeddingService;
    private final EmbeddingInputFormatter embeddingInputFormatter;
    private final FoodDataLoader foodDataLoader;
    private final FoodProductDocumentMapper documentMapper;
    private final BulkIndexingExecutor bulkIndexingExecutor;

    public ProductIndexingService(
            EmbeddingService embeddingService,
            EmbeddingInputFormatter embeddingInputFormatter,
            FoodDataLoader foodDataLoader,
            FoodProductDocumentMapper documentMapper,
            BulkIndexingExecutor bulkIndexingExecutor
    ) {
        this.embeddingService = embeddingService;
        this.embeddingInputFormatter = embeddingInputFormatter;
        this.foodDataLoader = foodDataLoader;
        this.documentMapper = documentMapper;
        this.bulkIndexingExecutor = bulkIndexingExecutor;
    }

    public long reindexData(String indexName) {
        return indexFoods(indexName, foodDataLoader.loadAll());
    }

    public long reindexData(String indexName, String dataPath) {
        List<FoodProduct> foods = dataPath == null || dataPath.isBlank()
                ? foodDataLoader.loadAll()
                : foodDataLoader.loadAll(dataPath);
        return indexFoods(indexName, foods);
    }

    private long indexFoods(String indexName, List<FoodProduct> foods) {
        if (foods.isEmpty()) {
            return 0;
        }

        long indexedCount = 0;
        List<IndexDocument> batch = new ArrayList<>(BULK_BATCH_SIZE);

        for (int index = 0; index < foods.size(); index++) {
            FoodProduct food = foods.get(index);
            batch.add(documentMapper.toIndexDocument(
                    food,
                    embeddingService.toEmbeddingVector(
                            embeddingInputFormatter.formatDocument(food.toEmbeddingText())
                    )
            ));

            if (batch.size() == BULK_BATCH_SIZE || index == foods.size() - 1) {
                indexedCount += bulkIndexingExecutor.bulkIndex(indexName, batch);
                log.info("Indexed batch for {}. progress={}/{}", indexName, index + 1, foods.size());
                batch.clear();
            }
        }

        return indexedCount;
    }
}
