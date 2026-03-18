# JsonCategoryBoostRules: AtomicReference 사용 이유

이 문서는 `JsonCategoryBoostRules`에서 왜 `AtomicReference`를 사용하는지 설명합니다.

현재 구조 기준으로 보면:

- 파일 읽기/JSON 파싱은 `store.source.FileCategoryBoostRuleSource`
- 캐시 보관/reload orchestration은 `JsonCategoryBoostRules`

로 역할이 나뉘어 있습니다.

## 1) 먼저 문제를 단순화해서 보기

`JsonCategoryBoostRules`는 두 가지를 동시에 만족해야 합니다.

1. 조회(`findByKeyword`)는 매우 자주 호출되므로 빨라야 한다.
2. 재로딩(`reload`) 중에도 조회 스레드가 깨진 상태(반쯤 바뀐 데이터)를 보면 안 된다.

즉, "많은 읽기 + 가끔 쓰기(교체)" 패턴입니다.

## 2) AtomicReference가 하는 일

코드의 핵심 필드:

```java
private final AtomicReference<CategoryBoostCacheEntry> currentEntry;
```

`CategoryBoostCacheEntry`는 "현재 사용 중인 룰 스냅샷"입니다.  
`AtomicReference`는 이 스냅샷 참조를 안전하게 읽고(`get`) 통째로 바꾸는(`set`) 역할을 합니다.

### 조회 시

```java
Map<String, Double> boosts = currentEntry.get().rulesByKeyword().get(keyword);
```

- 락을 잡지 않고 현재 스냅샷을 즉시 읽습니다.
- 조회 성능에 유리합니다.

### 재로딩 시

```java
if (!Objects.equals(cached.version(), newVersion)) {
    currentEntry.set(toCacheEntry(ruleSource.loadSnapshot()));
}
```

- 새 룰을 모두 읽어 완성한 뒤, 참조를 한 번에 교체합니다.
- 읽는 쪽은 "이전 스냅샷" 또는 "새 스냅샷" 중 하나만 보게 됩니다.
- 중간 상태(일부만 바뀐 Map)는 노출되지 않습니다.

## 3) 왜 Caffeine 캐시에 룰 자체를 안 넣고, AtomicReference를 쓰나?

이 클래스에서 Caffeine(`versionCheckGate`)은 **룰 데이터 저장소**가 아니라 **버전 체크 빈도 제한(TTL gate)** 용도입니다.

- `versionCheckGate`: "지금 버전 체크를 생략해도 되는가?"
- `currentEntry`: "실제 룰 스냅샷은 무엇인가?"

역할을 분리해서 코드가 단순해집니다.

즉, 실제 룰 데이터의 소유자는 `AtomicReference` 쪽이고,  
Caffeine은 "버전 확인을 너무 자주 하지 않도록 막는 장치"일 뿐입니다.

## 4) AtomicReference를 잘 모를 때 기억할 핵심

`AtomicReference<T>`는 "참조 타입(T)을 스레드 안전하게 교체/읽기" 위한 도구입니다.

주요 메서드:

- `get()`: 현재 참조 읽기
- `set(newValue)`: 참조를 새 값으로 교체
- `compareAndSet(old, new)`: 기대값과 같을 때만 교체(현재 코드는 미사용)

이 클래스는 `get/set`만 사용해도 목적을 충분히 달성합니다.

## 5) 이해 포인트 요약

1. `AtomicReference`는 "현재 룰 스냅샷"을 안전하게 교체하기 위한 장치다.
2. Caffeine은 "버전 체크 빈도 제한"용이며, 룰 본문 저장 역할이 아니다.
3. 현재는 파일 로딩이 source로 분리되어, `JsonCategoryBoostRules`는 캐시/reload coordination에 집중한다.
4. 결과적으로 이 클래스는 "빠른 읽기 + 안전한 스냅샷 교체"를 목표로 설계되어 있다.

패키지로도 이 의도가 드러납니다.

- `...categoryboost.store`
  - 캐시와 재로딩을 담당하는 진입점
- `...categoryboost.store.source`
  - 실제 룰 원천을 읽는 협력 객체들
