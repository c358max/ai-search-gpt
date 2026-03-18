package com.example.aisearch.support.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class K8sPortForwarder {

    private static final Logger log = LoggerFactory.getLogger(K8sPortForwarder.class);

    // 포트포워딩 프로세스를 한 번만 시작하도록 보관하는 핸들
    private final AtomicReference<ElasticsearchK8sHelper.PortForwardHandle> handleRef = new AtomicReference<>();

    // kubectl이 실행 가능한지 사전 확인
    public void requireKubectl() {
        ElasticsearchK8sHelper.requireKubectl();
    }

    // 서비스 이름이 비어있으면 k8s에서 ES HTTP 서비스를 자동 탐지
    public String resolveServiceName(String namespace, String configuredServiceName)
            throws Exception {
        if (configuredServiceName == null || configuredServiceName.isBlank()) {
            return ElasticsearchK8sHelper.findEsHttpService(namespace);
        }
        return configuredServiceName;
    }

    // 아직 포트포워딩이 없으면 시작한다 (중복 실행 방지)
    public void ensurePortForward(String namespace, String serviceName, int localPort, int remotePort)
            throws Exception {
        ElasticsearchK8sHelper.PortForwardHandle existing = handleRef.get();
        if (existing != null && existing.process() != null && existing.process().isAlive()) {
            return;
        }
        handleRef.set(null);
        ElasticsearchK8sHelper.PortForwardHandle handle =
                ElasticsearchK8sHelper.startPortForward(namespace, serviceName, localPort, remotePort);
        // 최초 성공한 핸들만 저장
        if (!handleRef.compareAndSet(null, handle)) {
            handle.close();
        }
        // 포트포워딩이 준비될 시간을 약간 준다
        Thread.sleep(3000L);
        log.info("[ES_AUTO] port-forward started: {} -> {}", localPort, remotePort);
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        // 애플리케이션 종료 시 포트포워딩 프로세스를 정리
        ElasticsearchK8sHelper.PortForwardHandle handle = handleRef.getAndSet(null);
        if (handle != null) {
            handle.close();
            log.info("[ES_AUTO] port-forward closed");
        }
    }
}
