package com.example.aisearch.service.indexing.bootstrap.ingest;

import com.example.aisearch.model.FoodProduct;
import com.example.aisearch.service.embedding.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductIndexingService {

    private final EmbeddingService embeddingService;
    private final FoodDataLoader foodDataLoader;
    private final FoodProductDocumentMapper documentMapper;
    private final BulkIndexingExecutor bulkIndexingExecutor;

    public ProductIndexingService(
            EmbeddingService embeddingService,
            FoodDataLoader foodDataLoader,
            FoodProductDocumentMapper documentMapper,
            BulkIndexingExecutor bulkIndexingExecutor
    ) {
        this.embeddingService = embeddingService;
        this.foodDataLoader = foodDataLoader;
        this.documentMapper = documentMapper;
        this.bulkIndexingExecutor = bulkIndexingExecutor;
    }

    public long reindexData(String indexName) {
        return reindexData(indexName, null);
    }

    public long reindexData(String indexName, String dataPath) {
        // 샘플 데이터 로딩
        List<FoodProduct> foods = dataPath == null || dataPath.isBlank()
                ? foodDataLoader.loadAll()
                : foodDataLoader.loadAll(dataPath);
        if (foods.isEmpty()) {
            return 0;
        }

        List<IndexDocument> documents = foods.stream()
                .map(food -> documentMapper.toIndexDocument(
                        food,
                        embeddingService.toEmbeddingVector(food.toEmbeddingText())
                ))
                .collect(Collectors.toList());

        return bulkIndexingExecutor.bulkIndex(indexName, documents);
    }
}
