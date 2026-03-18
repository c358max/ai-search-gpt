package com.example.aisearch.service.search.categoryboost.store.source;

import java.io.IOException;

/**
 * 카테고리 부스팅 룰의 원천(source)을 추상화한 계약입니다.
 *
 * 구현체는 파일, DB, 외부 API 등에서 룰을 읽을 수 있습니다.
 * 캐시/재로딩 정책은 이 인터페이스의 구현체가 아니라 상위 저장소에서 담당합니다.
 */
public interface CategoryBoostRuleSource {

    /**
     * 현재 원천 데이터의 version 값을 읽습니다.
     *
     * 저장소는 이 값을 비교해 전체 룰을 다시 읽어야 하는지 판단합니다.
     */
    String readVersion() throws IOException;

    /**
     * 현재 원천 데이터 전체를 읽어 메모리 캐시에 올릴 스냅샷으로 변환합니다.
     */
    CategoryBoostRuleSnapshot loadSnapshot() throws IOException;

    /**
     * 로그/디버깅용 설명 문자열입니다.
     */
    String description();
}
