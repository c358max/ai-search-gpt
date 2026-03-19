package com.example.aisearch.service.synonym;

import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.synonym.exception.InvalidSynonymReloadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SynonymReloadService {

    private static final String SUCCESS_MESSAGE = "synonyms reloaded successfully";
    private static final int SYNONYMS_RETRY_COUNT = 5;
    private static final long SYNONYMS_RETRY_DELAY_MILLIS = 1500L;

    private final AiSearchProperties properties;
    private final SynonymRuleSource synonymRuleSource;
    private final SynonymEsGateway synonymEsGateway;

    public SynonymReloadService(
            AiSearchProperties properties,
            SynonymRuleSource synonymRuleSource,
            SynonymEsGateway synonymEsGateway
    ) {
        this.properties = properties;
        this.synonymRuleSource = synonymRuleSource;
        this.synonymEsGateway = synonymEsGateway;
    }

    public SynonymReloadResult reload(SynonymReloadRequest request) {
        SynonymReloadMode mode = request.mode() == null ? SynonymReloadMode.PRODUCTION : request.mode();
        String indexName = resolveDefaultReloadIndex();
        String synonymsSet = resolveRequired(request.synonymsSet(), properties.synonymsSet(), "synonymsSet");
        List<String> rules = synonymRuleSource.loadRules(mode);
        if (rules.isEmpty()) {
            throw new InvalidSynonymReloadRequestException("동의어 규칙이 비어 있습니다. mode=" + mode);
        }

        synonymEsGateway.putSynonyms(synonymsSet, rules);
        synonymEsGateway.reloadSearchAnalyzers(indexName);

        return new SynonymReloadResult(
                true,
                true,
                mode.name(),
                synonymsSet,
                indexName,
                rules.size(),
                SUCCESS_MESSAGE
        );
    }

    public void ensureProductionSynonymsSet() {
        String synonymsSet = resolveRequired(null, properties.synonymsSet(), "synonymsSet");
        if (existsSynonymsWithRetry(synonymsSet)) {
            return;
        }

        List<String> rules = synonymRuleSource.loadRules(SynonymReloadMode.PRODUCTION);
        if (rules.isEmpty()) {
            throw new InvalidSynonymReloadRequestException("운영 동의어 규칙이 비어 있습니다.");
        }
        putSynonymsWithRetry(synonymsSet, rules);
    }

    private boolean existsSynonymsWithRetry(String synonymsSet) {
        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= SYNONYMS_RETRY_COUNT; attempt++) {
            try {
                return synonymEsGateway.existsSynonyms(synonymsSet);
            } catch (IllegalStateException e) {
                if (!isRetriableSynonymFailure(e) || attempt == SYNONYMS_RETRY_COUNT) {
                    throw e;
                }
                lastFailure = e;
                sleepBeforeRetry(attempt, "동의어 세트 조회 재시도", synonymsSet);
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        return false;
    }

    private void putSynonymsWithRetry(String synonymsSet, List<String> rules) {
        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= SYNONYMS_RETRY_COUNT; attempt++) {
            try {
                synonymEsGateway.putSynonyms(synonymsSet, rules);
                return;
            } catch (IllegalStateException e) {
                if (!isRetriableSynonymFailure(e) || attempt == SYNONYMS_RETRY_COUNT) {
                    throw e;
                }
                lastFailure = e;
                sleepBeforeRetry(attempt, "동의어 세트 반영 재시도", synonymsSet);
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    private boolean isRetriableSynonymFailure(IllegalStateException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        return message.contains("status: 503")
                || message.contains("[503]")
                || message.contains("503 Service Unavailable")
                || message.contains("all shards failed")
                || message.contains("no_shard_available_action_exception")
                || message.contains(".synonyms-2");
    }

    private void sleepBeforeRetry(int attempt, String action, String synonymsSet) {
        try {
            Thread.sleep(SYNONYMS_RETRY_DELAY_MILLIS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + " 중 인터럽트 발생. attempt=" + attempt + ", synonymsSet=" + synonymsSet,
                    interruptedException);
        }
    }

    private String resolveRequired(String requestValue, String propertyValue, String fieldName) {
        String resolved = (requestValue == null || requestValue.isBlank()) ? propertyValue : requestValue;
        if (resolved == null || resolved.isBlank()) {
            throw new InvalidSynonymReloadRequestException(fieldName + " 값이 비어 있습니다.");
        }
        return resolved;
    }

    private String resolveDefaultReloadIndex() {
        String readAlias = properties.readAlias();
        if (readAlias == null || readAlias.isBlank()) {
            throw new IllegalStateException("ai-search.read-alias 값이 비어 있습니다.");
        }
        return readAlias;
    }
}
