package com.example.aisearch.service.search.exception;

public class InvalidSearchRequestException extends IllegalArgumentException {

    public InvalidSearchRequestException(String message) {
        super(message);
    }
}
