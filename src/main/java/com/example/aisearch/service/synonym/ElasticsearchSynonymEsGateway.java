package com.example.aisearch.service.synonym;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import co.elastic.clients.transport.TransportException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class ElasticsearchSynonymEsGateway implements SynonymEsGateway {

    private final ElasticsearchClient client;

    public ElasticsearchSynonymEsGateway(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public boolean existsSynonyms(String synonymsSetId) {
        try {
            client.synonyms().getSynonym(request -> request.id(synonymsSetId));
            return true;
        } catch (TransportException e) {
            if (isNotFound(e)) {
                return false;
            }
            throw new IllegalStateException("동의어 세트 조회 실패: " + synonymsSetId + " | " + summarize(e), e);
        } catch (IOException | ElasticsearchException e) {
            if (isNotFound(e)) {
                return false;
            }
            throw new IllegalStateException("동의어 세트 조회 실패: " + synonymsSetId + " | " + summarize(e), e);
        }
    }

    @Override
    public void putSynonyms(String synonymsSetId, List<String> rules) {
        try {
            client.synonyms().putSynonym(request -> request
                    .id(synonymsSetId)
                    .synonymsSet(toSynonymRules(rules))
            );
        } catch (TransportException e) {
            if (isKnownPutSynonymDecodeIssue(e)) {
                return;
            }
            throw new IllegalStateException("동의어 세트 반영 실패: " + synonymsSetId + " | " + summarize(e), e);
        } catch (IOException | ElasticsearchException e) {
            throw new IllegalStateException("동의어 세트 반영 실패: " + synonymsSetId + " | " + summarize(e), e);
        }
    }

    @Override
    public void reloadSearchAnalyzers(String indexName) {
        try {
            client.indices().reloadSearchAnalyzers(request -> request.index(indexName));
        } catch (IOException | ElasticsearchException e) {
            throw new IllegalStateException("failed to reload analyzers for index " + indexName, e);
        }
    }

    private List<SynonymRule> toSynonymRules(List<String> rules) {
        return IntStream.range(0, rules.size())
                .mapToObj(i -> SynonymRule.of(rule -> rule
                        .id("rule-" + (i + 1))
                        .synonyms(rules.get(i))
                ))
                .toList();
    }

    private boolean isKnownPutSynonymDecodeIssue(TransportException e) {
        String topMessage = e.getMessage();
        if (topMessage == null) {
            return false;
        }

        boolean topLevelMatches = (topMessage.contains("status: 200") || topMessage.contains("status: 201"))
                && topMessage.contains("es/synonyms.put_synonym")
                && topMessage.contains("Failed to decode response");
        if (!topLevelMatches) {
            return false;
        }

        Throwable current = e;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains("Missing required property 'ReloadDetails.index'")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isNotFound(Exception e) {
        String message = e.getMessage();
        if (message != null && (message.contains("status: 404") || message.contains("[404]") || message.contains("404 Not Found"))) {
            return true;
        }

        Throwable current = e;
        while (current != null) {
            String currentMessage = current.getMessage();
            if (currentMessage != null
                    && (currentMessage.contains("status: 404")
                    || currentMessage.contains("[404]")
                    || currentMessage.contains("404 Not Found")
                    || currentMessage.contains("resource_not_found_exception"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String summarize(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getMessage();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }
}
