package com.example.aisearch.support.connection;

import com.example.aisearch.config.AiSearchK8sProperties;
import com.example.aisearch.config.AiSearchProperties;
import com.example.aisearch.support.k8s.ElasticsearchK8sHelper;
import com.example.aisearch.support.k8s.K8sPortForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Elasticsearch 접속 정보를 자동으로 보정하는 오케스트레이터.
 *
 * <p>로컬 개발 환경에서 k8s 포트포워딩이 필요할 때:
 * - kubectl 사용 가능 여부 확인
 * - 서비스 이름 자동 탐지
 * - Secret에서 비밀번호 로딩
 * - localhost URL로 재조합
 *
 * <p>그 외 환경(원격/프로덕션 등)에서는 원래 URL을 그대로 사용한다.
 */
@Component
public class ElasticsearchAutoConnector {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchAutoConnector.class);

    private final K8sPortForwarder k8sPortForwarder;

    public ElasticsearchAutoConnector(K8sPortForwarder k8sPortForwarder) {
        this.k8sPortForwarder = k8sPortForwarder;
    }

    /**
     * Elasticsearch 접속 정보를 결정한다.
     *
     * <p>동작 순서:
     * <ol>
     *   <li>기본 URL/계정을 읽는다.</li>
     *   <li>자동 포트포워딩 조건이 아니면 원래 URL을 반환한다.</li>
     *   <li>조건이 맞으면 kubectl 확인 → 서비스 이름 결정 → 비밀번호 로딩 → 포트포워딩 실행.</li>
     *   <li>localhost URL로 재조합해 반환한다.</li>
     *   <li>실패 시 원래 URL로 폴백한다.</li>
     * </ol>
     */
    public ConnectionInfo getConnectionInfo(AiSearchProperties properties, AiSearchK8sProperties k8sProperties) {
        String url = defaultIfBlank(properties.elasticsearchUrl(), "http://localhost:9200");
        URI uri = URI.create(url);
        String username = defaultIfBlank(properties.username(), "elastic");
        String password = defaultIfBlank(properties.password(), "password");

        if (!shouldAutoForward(k8sProperties, uri)) {
            return new ConnectionInfo(uri.toString(), username, password);
        }

        try {
            k8sPortForwarder.requireKubectl();

            String namespace = k8sProperties.namespace();
            String serviceName = k8sPortForwarder.resolveServiceName(namespace, k8sProperties.serviceName());

            password = resolvePassword(namespace, k8sProperties.secretName(), password);

            k8sPortForwarder.ensurePortForward(
                    namespace,
                    serviceName,
                    k8sProperties.localPort(),
                    k8sProperties.remotePort()
            );
            String resolvedUrl = buildLocalUrl(uri, k8sProperties.localPort());
            return new ConnectionInfo(resolvedUrl, username, password);
        } catch (Exception e) {
            log.warn("[ES_AUTO] port-forward failed, falling back to {}", uri, e);
            return new ConnectionInfo(uri.toString(), username, password);
        }
    }

    private boolean shouldAutoForward(AiSearchK8sProperties k8sProperties, URI uri) {
        return k8sProperties.autoPortForward() && isLocalHost(uri.getHost());
    }

    private boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    private String resolvePassword(String namespace, String secretName, String currentPassword)
            throws Exception {
        if (!needsPassword(currentPassword)) {
            return currentPassword;
        }
        String password = ElasticsearchK8sHelper.readElasticPassword(namespace, secretName);
        log.info("[ES_AUTO] elastic password loaded from secret {}", secretName);
        return password;
    }

    private boolean needsPassword(String password) {
        return password == null || password.isBlank() || "password".equals(password);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String buildLocalUrl(URI baseUri, int localPort) {
        String scheme = baseUri.getScheme() == null ? "http" : baseUri.getScheme();
        return scheme + "://localhost:" + localPort;
    }

    /**
     * 최종 접속 정보를 담는 불변 레코드.
     */
    public record ConnectionInfo(String url, String username, String password) {
    }
}
