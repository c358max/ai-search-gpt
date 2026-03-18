## JsonCategoryBoostRules 구조 변경 메모

이 문서는 과거 `Supplier<String>` 기반 구현에서, 현재의 `CategoryBoostRuleSource` 기반 구조로 왜 바뀌었는지 설명합니다.

핵심 변화는 단순합니다.

- 과거:
  - `JsonCategoryBoostRules`가 파일 경로 관리, 파일 읽기, JSON 파싱, 캐시, reload를 모두 담당
- 현재:
  - `JsonCategoryBoostRules`는 캐시/reload coordinator 역할에 집중
  - 파일 읽기/파싱은 `store.source.FileCategoryBoostRuleSource`로 분리
  - 향후 DB 확장을 위해 `CategoryBoostRuleSource` 인터페이스 도입

## 1. 예전 구조의 한계

과거에는 `JsonCategoryBoostRules`가 파일 경로를 직접 들고 있었습니다.

```java
private volatile String ruleFilePath;
```

그리고 내부에서 직접:

- 현재 경로 확인
- 파일 열기
- version 읽기
- 전체 룰 읽기
- 룰 정규화

를 모두 처리했습니다.

이 구조는 작은 규모에서는 단순했지만, 나중에 DB 같은 다른 저장소를 붙이려면 아래 문제가 생깁니다.

- `if (file) ... else if (db) ...` 분기가 저장소 내부로 들어올 가능성
- 파일/DB별 읽기 로직과 캐시/reload 로직이 다시 섞일 가능성
- "어디서 읽는가"와 "언제 다시 읽는가"가 한 클래스에 같이 남는 문제

## 2. 현재 구조

현재는 source abstraction을 도입했습니다.

### 2.1 공통 계약

```java
public interface CategoryBoostRuleSource {
    String readVersion() throws IOException;
    CategoryBoostRuleSnapshot loadSnapshot() throws IOException;
    String description();
}
```

이 계약의 의미:

- `readVersion()`
  - 원천 데이터가 바뀌었는지 빠르게 판단하기 위한 version 조회
- `loadSnapshot()`
  - 현재 룰 전체를 메모리에 올릴 스냅샷으로 변환
- `description()`
  - 로그/디버깅용 설명 문자열

### 2.2 파일 구현체

현재 운영 구현체는 `store.source.FileCategoryBoostRuleSource`입니다.

```java
@Component
public class FileCategoryBoostRuleSource implements CategoryBoostRuleSource
```

책임:

- 파일 열기
- JSON 파싱
- 룰 정규화

비책임:

- TTL gate
- 메모리 캐시
- reload orchestration

### 2.3 캐시/reload 저장소

`JsonCategoryBoostRules`는 이제 source를 주입받아 사용합니다.

```java
@Autowired
public JsonCategoryBoostRules(
        CategoryBoostRuleSource ruleSource,
        AiSearchProperties properties
) {
    this(ruleSource, properties.categoryBoostCacheTtlSeconds());
}
```

즉, 현재 `JsonCategoryBoostRules`의 책임은 다음으로 줄었습니다.

- 현재 룰 캐시 보관
- TTL 동안 version check 생략
- 필요 시 `ruleSource`를 통해 version 확인
- 변경 감지 시 새 스냅샷으로 교체

## 3. 왜 Supplier보다 source abstraction이 더 적절한가

과거 `Supplier<String>`는 "경로를 필요할 때 꺼내 쓰는 방식"이었습니다.

이 방식은 "경로 값 1개를 바꾸는 문제"에는 유효했지만,  
지금 고민은 "파일 외의 다른 저장소를 붙일 수 있어야 한다"는 확장 문제입니다.

즉, 현재 필요한 추상화는:

- 경로 공급 추상화

가 아니라

- 룰 원천(source) 추상화

입니다.

이 차이가 중요합니다.

- `Supplier<String>`
  - 무엇을 읽을지(경로)를 늦게 결정
- `CategoryBoostRuleSource`
  - 어디서 읽을지(파일/DB 등)를 바꿀 수 있음

## 4. 테스트 편의성은 어떻게 유지하나

기존 테스트는 경로를 바꿔 reload를 확인했습니다.

현재도 이 흐름은 유지됩니다.

```java
JsonCategoryBoostRules rules = new JsonCategoryBoostRules(
        new DefaultResourceLoader(),
        new ObjectMapper(),
        "classpath:data/category_boosting_v1.json",
        300
);

rules.setRuleFilePath("classpath:data/category_boosting_v2.json");
rules.reload();
```

차이는 내부 구현뿐입니다.

- 예전: `JsonCategoryBoostRules`가 직접 경로를 들고 변경
- 현재: 내부의 `FileCategoryBoostRuleSource`가 경로를 변경

즉, 테스트 사용성은 유지하면서 역할 분리만 좋아진 상태입니다.

## 5. DbCategoryBoostRuleSource를 왜 미리 만들었나

현재는 파일 기반 구현만 활성화되어 있지만, DB 확장을 고려해 아래 클래스를 미리 만들었습니다.

```java
public class DbCategoryBoostRuleSource implements CategoryBoostRuleSource
```

의도:

- source abstraction이 실제로 DB 확장 지점을 제공한다는 걸 코드 구조로 명확히 하기 위함
- 나중에 DB 버전을 추가할 때 `JsonCategoryBoostRules` 캐시 로직을 다시 건드리지 않게 하기 위함

현재는 `@Component`를 붙이지 않았고, 구현도 비워둔 상태입니다.

## 6. 현재 구조의 장점 요약

1. `JsonCategoryBoostRules`가 "언제 다시 읽을지"에 집중한다.
2. `FileCategoryBoostRuleSource`가 "파일에서 어떻게 읽을지"에 집중한다.
3. 파일 외 저장소(DB)를 추가해도 캐시/reload 로직을 재사용할 수 있다.
4. 읽는 사람이 클래스 책임을 더 쉽게 구분할 수 있다.

## 7. 결론

이제 핵심 분리는 이렇게 이해하면 됩니다.

- `CategoryBoostRuleSource`
  - 룰 원천 추상화
- `FileCategoryBoostRuleSource`
  - 파일에서 version + 룰 스냅샷 읽기
- `JsonCategoryBoostRules`
  - 현재 룰 캐시 보관 + reload coordination

패키지 기준으로 보면 구조는 이렇게 읽으면 됩니다.

- `...categoryboost.store`
  - 외부에서 사용하는 진입점: `JsonCategoryBoostRules`
- `...categoryboost.store.source`
  - 진입점이 의존하는 룰 원천 계층
  - `CategoryBoostRuleSource`
  - `FileCategoryBoostRuleSource`
  - `DbCategoryBoostRuleSource`
  - `CategoryBoostRuleSnapshot`

즉, 현재 구조는 "Supplier 제거"보다 한 단계 더 나아가  
"저장소 원천(source)과 캐시/reload 정책을 분리"한 구조라고 보는 게 맞습니다.
