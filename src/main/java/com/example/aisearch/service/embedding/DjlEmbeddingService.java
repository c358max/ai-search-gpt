package com.example.aisearch.service.embedding;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.example.aisearch.service.embedding.model.EmbeddingModelSource;
import com.example.aisearch.service.embedding.model.EmbeddingModelSourceLoader;
import com.example.aisearch.service.embedding.EmbeddingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 텍스트를 숫자 벡터(임베딩)로 바꿔주는 서비스입니다.
 *
 * 왜 필요한가?
 * - 검색어와 상품 문장을 같은 숫자 공간으로 변환하면
 *   "의미가 비슷한지"를 유사도 점수로 계산할 수 있습니다.
 *
 * 이 클래스가 하는 일:
 * 1) 애플리케이션 시작 시 임베딩 모델을 메모리에 로드
 * 2) 입력 텍스트를 임베딩 벡터로 변환
 * 3) 벡터 길이를 1로 맞추는 정규화(L2) 수행
 * 4) 종료 시 모델/예측기 리소스 정리
 *
 * 모델 상세는 docs/01.embedding-model.md 참고
 */
@Service
@ConditionalOnProperty(prefix = "ai-search.embedding", name = "provider", havingValue = "djl", matchIfMissing = true)
public class DjlEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DjlEmbeddingService.class);

    private final EmbeddingModelSourceLoader modelSourceResolver;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private int dimensions;

    public DjlEmbeddingService(EmbeddingModelSourceLoader modelSourceResolver) {
        this.modelSourceResolver = modelSourceResolver;
    }

    @PostConstruct
    public void init() throws ModelNotFoundException, MalformedModelException, IOException {
        // DJL가 모델을 어떻게 로드할지 설정한다.
        // .optApplication(Application.NLP.TEXT_EMBEDDING)
        //      - 이 모델을 NLP 임베딩 용도로 사용한다고 DJL에 알려준다.
        //      - 내부적으로 적절한 토크나이저/translator 처리 경로 선택에 도움을 준다.
        //      - 즉, "텍스트(String) -> 벡터(float[])" 작업이라는 힌트다.
        // .optProgress(new ProgressBar())
        //      - 모델 로딩(또는 다운로드) 진행률을 콘솔에 출력한다.
        //      - 초기 로딩 시간이 긴 환경에서 운영/디버깅 가시성을 높여준다.
        //      - 검색 결과 자체에는 영향을 주지 않는다.
        Criteria.Builder<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optApplication(Application.NLP.TEXT_EMBEDDING)
                .optProgress(new ProgressBar());

        // 모델 소스를 읽어 경로 기반/URL 기반 로딩을 분기한다.
        // - 경로 기반: 로컬 파일(classpath 포함)에서 로드
        // - URL 기반: 원격 모델 주소에서 로드
        EmbeddingModelSource modelSource = modelSourceResolver.load();
        if (modelSource.isPathBased()) {
            criteria.optModelPath(modelSource.modelPath());

            if (modelSource.requiresTranslatorFactory()) {
                // 로컬 경로 모델 중 일부는 DJL이 translator를 자동 추론하지 못할 수 있다.
                // 이 경우 TextEmbeddingTranslatorFactory를 명시해
                // "입력 텍스트를 임베딩 벡터로 변환하는 방식"을 강제로 지정한다.
                // 결과적으로 모델 로딩/추론 실패를 줄이고 동작을 일관되게 만든다.
                criteria.optTranslatorFactory(new ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory());
            }
        } else {
            // Url 기반의 경우 translator 정보가 제공되기 때문에  .optTranslatorFactory(...) 과정이 없어도 된다.
            criteria.optModelUrls(modelSource.modelUrl());
        }

        Criteria<String, float[]> buildCriteria = criteria.build();

        // 모델/예측기 로딩
        model = buildCriteria.loadModel();
        predictor = model.newPredictor();

        // 모델이 생성하는 벡터 차원을 확인하기 위해 샘플 문장 1회 추론한다.
        // 차원 수는 Elasticsearch dense_vector 매핑과 반드시 일치해야 한다.
        float[] probe = predictRaw("한글 식품 벡터 검색 테스트");
        assertNonZeroVector(probe, "한글 식품 벡터 검색 테스트");
        dimensions = probe.length;

        log.info("Embedding model initialized. dimensions={}", dimensions);
    }

    @Override
    public List<Float> toEmbeddingVector(String text) {
        // 1) 모델 추론으로 원본 벡터 생성
        // 2) L2 정규화로 벡터 길이를 1로 맞춘다.
        //    코사인 유사도 계산 시 더 안정적이고 일관된 결과를 얻을 수 있다.
        float[] raw = predictRaw(text);
        assertNonZeroVector(raw, text);
        float[] normalized = l2Normalize(raw);

        List<Float> values = new ArrayList<>(normalized.length);
        for (float value : normalized) {
            values.add(value);
        }
        return values;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private float[] predictRaw(String text) {
        try {
            // DJL Predictor로 텍스트 임베딩을 생성한다.
            return predictor.predict(text);
        } catch (TranslateException e) {
            throw new IllegalStateException("임베딩 생성 실패", e);
        }
    }

    /**
     * 벡터의 크기값을 없애고, 방향값만 남기는 함수(메서드)
     * - 단어의 유사한 정도는 크기보다 방향이 더 적합하기 때문에 방향값만 사용함
     * - 노멀라이저 설명 :
     *  EmbeddingNormalizer.md
     */
    private float[] l2Normalize(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0.0) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    private void assertNonZeroVector(float[] vector, String text) {
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("임베딩 모델이 비어 있는 벡터를 반환했습니다. text=" + summarizeText(text));
        }

        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }

        if (sum == 0.0d) {
            throw new IllegalStateException(
                    "임베딩 모델이 zero vector를 반환했습니다. cosine 유사도 인덱싱을 계속할 수 없습니다. text="
                            + summarizeText(text)
            );
        }
    }

    private String summarizeText(String text) {
        if (text == null) {
            return "<null>";
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }

    @PreDestroy
    public void close() {
        // 애플리케이션 종료 시 모델/예측기 리소스를 명시적으로 해제한다.
        // 해제하지 않으면 테스트/재시작 시 메모리 사용량이 누적될 수 있다.
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }
}
