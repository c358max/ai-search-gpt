# ai-search-gpt

무료 라이선스(Basic) Elasticsearch + Spring Boot 기반 한글 식품 벡터 검색 토이 프로젝트입니다.

## 핵심 구성
- Elasticsearch: ECK로 Kubernetes에 배포
- 라이선스: Basic(무료)
- 임베딩: DJL + 비교 후보 모델(`multilingual-e5-small-ko-v2`, `KURE-v1`, `bge-m3`)
- 검색: Elasticsearch `dense_vector` + `knn` 쿼리
- 데이터: `src/main/resources/data/goods_template.json`

## 빠른 시작
1. `./sh_bin/00_2_install_eck_operator.sh` (1회성)
2. `./sh_bin/00_3_build_elasticsearch_nori_image.sh` (1회성)
3. `ES_CUSTOM_IMAGE=<registry>/<repo>/ai-search-es:8.13.4-nori ./sh_bin/00_4_push_elasticsearch_nori_image.sh` (선택)
4. `./sh_bin/00_6_start_elasticsearch_cluster_custom_image.sh`
5. `./sh_bin/10_1_prepare_djl_truststore.sh` (1회성)
6. `./sh_bin/01_check_elasticsearch_status.sh`
7. `./sh_bin/02_generate_sample_data.sh`
8. `./sh_bin/04_run_vector_search_test_local.sh`

상세 절차는 `sh_bin/readme.md` 참고.

## 테스트 실행
- 기본 검증(로컬 ES 없이 가능): `./gradlew test`
- 통합 검증(Elasticsearch 필요): `./gradlew integrationTest`

통합 테스트는 로컬 Elasticsearch, truststore, 샘플 색인 환경이 준비된 상태를 전제로 합니다.

## 모델별 실행 프로필
- `model-e5-small-ko-v2`: `./gradlew bootRun --args='--spring.profiles.active=model-e5-small-ko-v2'`
- `model-kure-v1`: `./gradlew bootRun --args='--spring.profiles.active=model-kure-v1'`
- `model-bge-m3`: `./gradlew bootRun --args='--spring.profiles.active=model-bge-m3'`

기본 포트/인덱스/alias는 프로필마다 분리되어 있습니다.
- `model-e5-small-ko-v2`: `8091`, `food-products-e5-small-ko-v2`
- `model-kure-v1`: `8092`, `food-products-kure-v1`
- `model-bge-m3`: `8093`, `food-products-bge-m3`

색인까지 같이 하려면 프로필을 조합해서 실행합니다.
- 예: `./gradlew bootRun --args='--spring.profiles.active=model-kure-v1,indexing-web'`

모델을 바꾸면 임베딩 차원이 달라질 수 있으므로 각 프로필은 별도 인덱스/alias를 사용해야 하며, 최초 1회 재색인이 필요합니다.

벡터 전용 검색(BM25 제외)을 쓰려면 `search-vector-only` 프로필을 함께 사용합니다.
- 예: `./gradlew bootRun --args='--spring.profiles.active=model-e5-small-ko-v2,search-vector-only'`
- 예: `./gradlew bootRun --args='--spring.profiles.active=model-kure-v1,search-vector-only,indexing-web'`

모델 실행 스크립트도 사용할 수 있습니다.
- 웹 실행: `./sh_bin/21_run_model_web.sh e5-small-ko-v2`
- 색인 + 웹 실행: `./sh_bin/22_run_model_indexing_web.sh kure-v1`
- 색인만 실행: `./sh_bin/23_run_model_indexing_only.sh bge-m3`
- 전체 모델 비교 웹 실행(VectorOnlySearchStrategy): `./sh_bin/30_start_all_model_web.sh`
- 전체 모델 색인 + 비교 웹 실행(VectorOnlySearchStrategy): `./sh_bin/31_start_all_model_indexing_web.sh`
- 전체 모델 종료: `./sh_bin/32_stop_all_models.sh`
- 모델 1개 쿼리 비교: `./sh_bin/33_compare_model_search.sh '어린이 간식'`
- 대표 쿼리 일괄 비교: `./sh_bin/34_compare_model_search_queries.sh`
- 모델 상태 확인: `./sh_bin/35_check_model_status.sh`

기획자 검토 가이드:
- [docs/13.model-review-guide.md](/Users/davidnam/Project/pulmuone/ai-search-gpt/docs/13.model-review-guide.md)
- Docker 개발서버 배포: [docs/14.docker-dev-deploy.md](/Users/davidnam/Project/pulmuone/ai-search-gpt/docs/14.docker-dev-deploy.md)
- 비교 화면: `http://localhost:8091/model-compare.html`
