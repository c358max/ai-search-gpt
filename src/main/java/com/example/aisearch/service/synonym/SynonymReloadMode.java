package com.example.aisearch.service.synonym;

import com.example.aisearch.service.synonym.exception.InvalidSynonymReloadRequestException;

import java.util.Locale;

public enum SynonymReloadMode {
    PRODUCTION,
    REGRESSION;

    public static SynonymReloadMode fromNullable(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return PRODUCTION;
        }
        try {
            return SynonymReloadMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSynonymReloadRequestException("invalid reload mode: " + rawMode);
        }
    }
}
