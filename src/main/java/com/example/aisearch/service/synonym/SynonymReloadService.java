package com.example.aisearch.service.synonym;

import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.synonym.exception.InvalidSynonymReloadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SynonymReloadService {

    private static final String SUCCESS_MESSAGE = "synonyms reloaded successfully";

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
        if (synonymEsGateway.existsSynonyms(synonymsSet)) {
            return;
        }

        List<String> rules = synonymRuleSource.loadRules(SynonymReloadMode.PRODUCTION);
        if (rules.isEmpty()) {
            throw new InvalidSynonymReloadRequestException("운영 동의어 규칙이 비어 있습니다.");
        }
        synonymEsGateway.putSynonyms(synonymsSet, rules);
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
