package com.example.aisearch.service.indexing.orchestration.exception;

public class InvalidRestoreTargetException extends IllegalArgumentException {

    public InvalidRestoreTargetException(String message) {
        super(message);
    }
}
