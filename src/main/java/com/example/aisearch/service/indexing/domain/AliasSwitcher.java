package com.example.aisearch.service.indexing.domain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.service.indexing.domain.exception.AliasLookupException;
import com.example.aisearch.service.indexing.domain.exception.AliasSwapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * read alias 조회/전환을 담당하는 도메인 서비스.
 *
 * 목적:
 * - 검색 트래픽이 바라보는 인덱스를 안전하게 교체
 * - 롤아웃 시점의 무중단 전환 지원
 */
@Service
public class AliasSwitcher {

    private static final Logger log = LoggerFactory.getLogger(AliasSwitcher.class);

    private final ElasticsearchClient esClient;
    private final AiSearchProperties properties;

    public AliasSwitcher(ElasticsearchClient esClient, AiSearchProperties properties) {
        this.esClient = esClient;
        this.properties = properties;
    }

    /**
     * 현재 read alias가 가리키는 단일 인덱스를 조회한다.
     *
     * @return alias 대상 인덱스명, alias가 없으면 null
     * @throws AliasLookupException alias 조회 실패 또는 다중 인덱스 연결 시
     */
    public String findCurrentAliasedIndex() {
        String alias = resolveReadAlias();
        try {
            boolean existsAlias = esClient.indices().existsAlias(e -> e.name(alias)).value();
            if (!existsAlias) {
                return null;
            }

            return selectCurrentAliasedIndex(alias, loadAliasTargets(alias));
        } catch (IOException e) {
            throw new AliasLookupException("alias 조회 실패. alias=" + alias, e);
        }
    }

    /**
     * read alias를 기존 인덱스에서 신규 인덱스로 전환한다.
     *
     * 마이그레이션 호환:
     * - alias 이름과 동일한 물리 인덱스가 있으면 필요 시 제거 후 alias를 생성한다.
     *
     * @param oldIndex 기존 alias 대상 인덱스명(null 허용)
     * @param newIndex 신규 alias 대상 인덱스명
     * @throws AliasSwapException alias 전환 실패 시
     */
    public void swapReadAlias(String oldIndex, String newIndex) {
        String alias = resolveReadAlias();
        try {
            boolean aliasNameAsIndexExists = esClient.indices().exists(e -> e.index(alias)).value();
            List<String> currentAliasTargets = loadAliasTargets(alias);
            esClient.indices().updateAliases(u -> {
                // 마이그레이션 단계: alias 이름과 동일한 물리 인덱스가 있으면 먼저 제거해야 alias를 생성할 수 있다.
                if (aliasNameAsIndexExists && (oldIndex == null || oldIndex.isBlank())) {
                    u.actions(a -> a.removeIndex(r -> r.index(alias)));
                }

                for (String currentAliasTarget : currentAliasTargets) {
                    if (!currentAliasTarget.equals(newIndex)) {
                        u.actions(a -> a.remove(r -> r.alias(alias).index(currentAliasTarget)));
                    }
                }
                if ((currentAliasTargets.isEmpty())
                        && oldIndex != null
                        && !oldIndex.isBlank()
                        && !oldIndex.equals(newIndex)) {
                    u.actions(a -> a.remove(r -> r.alias(alias).index(oldIndex)));
                }
                u.actions(a -> a.add(ad -> ad.alias(alias).index(newIndex)));
                return u;
            });
        } catch (IOException e) {
            throw new AliasSwapException("alias 전환 실패. alias=" + alias
                    + ", oldIndex=" + oldIndex + ", newIndex=" + newIndex, e);
        }
    }

    private String resolveReadAlias() {
        String readAlias = properties.readAlias();
        if (readAlias == null || readAlias.isBlank()) {
            throw new IllegalStateException("ai-search.read-alias 값이 비어 있습니다.");
        }
        return readAlias;
    }

    private List<String> loadAliasTargets(String alias) throws IOException {
        Map<String, ?> aliasMap = esClient.indices().getAlias(g -> g.name(alias)).result();
        return aliasMap.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String selectCurrentAliasedIndex(String alias, List<String> aliasTargets) {
        if (aliasTargets.isEmpty()) {
            return null;
        }
        if (aliasTargets.size() == 1) {
            return aliasTargets.getFirst();
        }

        String selected = aliasTargets.stream()
                .max(Comparator.naturalOrder())
                .orElse(aliasTargets.getFirst());
        log.warn("read alias가 여러 인덱스를 가리키고 있어 최신 인덱스를 선택합니다. alias={}, selected={}, indices={}",
                alias, selected, aliasTargets);
        return selected;
    }
}
