package com.example.aisearch.service.indexing;

import com.example.aisearch.service.indexing.orchestration.IndexRolloutService;
import com.example.aisearch.service.indexing.orchestration.result.IndexRolloutResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile({"indexing", "indexing-web"})
@ConditionalOnProperty(prefix = "ai-search", name = "run-index", havingValue = "true")
public class IndexingRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexingRunner.class);

    private final IndexRolloutService indexRolloutService;
    private final ConfigurableApplicationContext context;
    private final Environment environment;

    public IndexingRunner(IndexRolloutService indexRolloutService,
                          ConfigurableApplicationContext context,
                          Environment environment) {
        this.indexRolloutService = indexRolloutService;
        this.context = context;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        IndexRolloutResult result = indexRolloutService.rollOutFromSourceData();
        log.info("Index rollout complete. oldIndex={}, newIndex={}, indexedCount={}",
                result.oldIndex(), result.newIndex(), result.indexedCount());

        if (environment.acceptsProfiles("indexing")) {
            // 색인 전용 실행은 배치 작업 완료 후 종료한다.
            int exitCode = SpringApplication.exit(context, () -> 0);
            System.exit(exitCode);
        }

        log.info("Indexing finished in indexing-web mode. Keeping application alive for web requests.");
    }
}
