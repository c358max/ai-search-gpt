package com.example.aisearch.service.indexing.orchestration;

import com.example.aisearch.service.indexing.bootstrap.ingest.ProductIndexingService;
import com.example.aisearch.service.indexing.domain.AliasSwitcher;
import com.example.aisearch.service.indexing.domain.IndexCleanupService;
import com.example.aisearch.service.indexing.domain.IndexCreator;
import com.example.aisearch.service.indexing.orchestration.result.IndexRolloutResult;
import org.springframework.stereotype.Service;

/**
 * 소스 데이터 기준으로 검색 인덱스를 롤아웃한다.
 *
 * 처리 순서:
 * 1) 현재 read alias가 가리키는 기존 인덱스 조회
 * 2) 신규 버전 인덱스 생성(매핑/세팅 반영)
 * 3) 소스 데이터를 신규 인덱스로 색인
 * 4) read alias를 기존 -> 신규 인덱스로 전환
 * 5) 보관 정책에 따라 과거 버전 인덱스 정리
 *
 * 목적:
 * - 무중단에 가까운 인덱스 교체
 * - 인덱스 스키마 변경/데이터 재적재를 안전하게 반영
 */
@Service
public class IndexRolloutService {

    private final IndexCreator indexCreator;
    private final AliasSwitcher aliasSwitcher;
    private final IndexCleanupService indexCleanupService;
    private final ProductIndexingService productIndexingService;

    public IndexRolloutService(
            IndexCreator indexCreator,
            AliasSwitcher aliasSwitcher,
            IndexCleanupService indexCleanupService,
            ProductIndexingService productIndexingService
    ) {
        this.indexCreator = indexCreator;
        this.aliasSwitcher = aliasSwitcher;
        this.indexCleanupService = indexCleanupService;
        this.productIndexingService = productIndexingService;
    }

    /**
     * 소스 데이터로 인덱스 롤아웃을 수행하고 결과를 반환한다.
     *
     * @return oldIndex, newIndex, indexedCount를 포함한 롤아웃 결과
     * @throws RuntimeException 인덱스 생성/색인/alias 전환/정리 중 실패 시
     */
    public IndexRolloutResult rollOutFromSourceData() {
        return rollOutFromSourceData(null);
    }

    public IndexRolloutResult rollOutFromSourceData(String dataPath) {
        String oldIndex = aliasSwitcher.findCurrentAliasedIndex();
        String newIndex = indexCreator.createVersionedIndex();

        long indexedCount = productIndexingService.reindexData(newIndex, dataPath);

        aliasSwitcher.swapReadAlias(oldIndex, newIndex);
        indexCleanupService.cleanupOldVersionedIndices(newIndex);

        return new IndexRolloutResult(oldIndex, newIndex, indexedCount);
    }
}
