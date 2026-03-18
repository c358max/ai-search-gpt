package com.example.aisearch.controller;

import com.example.aisearch.controller.dto.ReloadSynonymsRequestDto;
import com.example.aisearch.controller.dto.ReloadSynonymsResponseDto;
import com.example.aisearch.controller.dto.SearchResponseDto;
import com.example.aisearch.controller.dto.ModelRuntimeInfoResponseDto;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.model.search.SearchPageResult;
import com.example.aisearch.model.search.SearchPagingPolicy;
import com.example.aisearch.model.search.SearchPrice;
import com.example.aisearch.model.search.ProductSearchRequest;
import com.example.aisearch.model.search.SearchSortOption;
import com.example.aisearch.service.embedding.EmbeddingService;
import com.example.aisearch.service.search.ProductSearchService;
import com.example.aisearch.service.synonym.SynonymReloadRequest;
import com.example.aisearch.service.synonym.SynonymReloadResult;
import com.example.aisearch.service.synonym.SynonymReloadService;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@CrossOrigin(origins = "*")
public class SearchController {

    private final ProductSearchService productSearchService;
    private final SynonymReloadService synonymReloadService;
    private final EmbeddingService embeddingService;
    private final AiSearchProperties aiSearchProperties;

    public SearchController(
            ProductSearchService productSearchService,
            SynonymReloadService synonymReloadService,
            EmbeddingService embeddingService,
            AiSearchProperties aiSearchProperties
    ) {
        this.productSearchService = productSearchService;
        this.synonymReloadService = synonymReloadService;
        this.embeddingService = embeddingService;
        this.aiSearchProperties = aiSearchProperties;
    }

    @GetMapping("/api/search")
    public SearchResponseDto search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "5") Integer size,
            @RequestParam(value = "minPrice", required = false) @Min(0) Integer minPrice,
            @RequestParam(value = "maxPrice", required = false) @Min(0) Integer maxPrice,
            @RequestParam(value = "categoryId", required = false) List<Integer> categoryIds,
            @RequestParam(value = "sort", defaultValue = "RELEVANCE_DESC") SearchSortOption sortOption
    ) {
        SearchPrice searchPrice = (minPrice == null && maxPrice == null)
                ? null
                : new SearchPrice(minPrice, maxPrice);
        ProductSearchRequest request = new ProductSearchRequest(query, searchPrice, categoryIds, sortOption);
        Pageable pageable = SearchPagingPolicy.toPageable(page, size);

        SearchPageResult pageResult = productSearchService.searchPage(request, pageable);
        List<Integer> normalizedCategoryIds = categoryIds == null ? List.of() : categoryIds;
        return new SearchResponseDto(
                query,
                pageResult.page(),
                pageResult.size(),
                minPrice,
                maxPrice,
                normalizedCategoryIds,
                sortOption,
                pageResult.totalElements(),
                pageResult.totalPages(),
                pageResult.results().size(),
                pageResult.results()
        );
    }

    @GetMapping("/api/search/model-info")
    public ModelRuntimeInfoResponseDto modelInfo() {
        Runtime runtime = Runtime.getRuntime();
        long heapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        long heapMaxBytes = runtime.maxMemory();
        String modelSource = aiSearchProperties.embeddingModelPath() != null
                ? aiSearchProperties.embeddingModelPath()
                : aiSearchProperties.embeddingModelUrl();

        return new ModelRuntimeInfoResponseDto(
                embeddingService.dimensions(),
                heapUsedBytes,
                heapMaxBytes,
                modelSource
        );
    }

    @PostMapping("/api/search/reload-synonyms")
    public ReloadSynonymsResponseDto reloadSynonyms(
            @RequestBody(required = false) ReloadSynonymsRequestDto requestDto
    ) {
        SynonymReloadRequest request = requestDto == null
                ? SynonymReloadRequest.defaultRequest()
                : requestDto.toServiceRequest();
        SynonymReloadResult result = synonymReloadService.reload(request);
        return ReloadSynonymsResponseDto.from(result);
    }
}
