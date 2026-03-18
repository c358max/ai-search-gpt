package com.example.aisearch.service.indexing.orchestration.exception;

public class RestoreTargetNotFoundException extends IllegalArgumentException {

    public RestoreTargetNotFoundException(String message) {
        super(message);
    }
}
