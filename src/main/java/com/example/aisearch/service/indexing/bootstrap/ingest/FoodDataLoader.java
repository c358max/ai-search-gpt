package com.example.aisearch.service.indexing.bootstrap.ingest;

import com.example.aisearch.model.FoodProduct;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class FoodDataLoader {

    private static final String DEFAULT_DATA_PATH = "classpath:data/goods_template.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public FoodDataLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public List<FoodProduct> loadAll() {
        return loadAll(DEFAULT_DATA_PATH);
    }

    public List<FoodProduct> loadAll(String path) {
        Resource resource = resolveResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("샘플 데이터 로딩 실패. path=" + path, e);
        }
    }

    private Resource resolveResource(String path) {
        String resolved = (path == null || path.isBlank()) ? DEFAULT_DATA_PATH : path;
        String location = resolved.contains(":") ? resolved : "classpath:" + resolved;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("샘플 데이터 파일이 없습니다. path=" + resolved);
        }
        return resource;
    }
}
