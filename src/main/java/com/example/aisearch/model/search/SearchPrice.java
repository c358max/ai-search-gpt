package com.example.aisearch.model.search;

import com.example.aisearch.service.search.exception.InvalidSearchRequestException;

public record SearchPrice(Integer minPrice, Integer maxPrice) {

    public SearchPrice {
        if (minPrice != null && minPrice < 0) {
            throw new InvalidSearchRequestException("minPrice는 0 이상이어야 합니다.");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new InvalidSearchRequestException("maxPrice는 0 이상이어야 합니다.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new InvalidSearchRequestException("minPrice는 maxPrice보다 클 수 없습니다.");
        }
    }

    public boolean isEmpty() {
        return minPrice == null && maxPrice == null;
    }
}
