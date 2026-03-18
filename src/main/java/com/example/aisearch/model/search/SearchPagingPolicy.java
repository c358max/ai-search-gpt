package com.example.aisearch.model.search;

import com.example.aisearch.service.search.exception.InvalidSearchRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class SearchPagingPolicy {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 5;
    public static final int MAX_SIZE = 40;

    private SearchPagingPolicy() {
    }

    public static Pageable toPageable(Integer page, Integer size) {
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        int normalizedSize = size == null ? DEFAULT_SIZE : size;

        if (normalizedPage < 1) {
            throw new InvalidSearchRequestException("page는 1 이상이어야 합니다.");
        }
        if (normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new InvalidSearchRequestException("size는 1~40 범위여야 합니다.");
        }

        return PageRequest.of(normalizedPage - 1, normalizedSize);
    }
}
