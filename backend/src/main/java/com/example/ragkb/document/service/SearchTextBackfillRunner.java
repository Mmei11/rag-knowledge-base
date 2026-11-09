package com.example.ragkb.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.search.backfill", name = "enabled", havingValue = "true")
public class SearchTextBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchTextBackfillRunner.class);

    private final SearchTextBackfillService backfillService;
    private final int batchSize;

    public SearchTextBackfillRunner(
            SearchTextBackfillService backfillService,
            @Value("${app.search.backfill.batch-size:500}") int batchSize) {
        this.backfillService = backfillService;
        this.batchSize = batchSize;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting search_text backfill, batchSize={}", batchSize);
        SearchTextBackfillService.BackfillResult result = backfillService.backfill(batchSize, false);
        log.info("search_text backfill runner finished, scanned={}, updated={}, elapsedMs={}, lockAcquired={}",
                result.scanned(),
                result.updated(),
                result.elapsedMs(),
                result.lockAcquired());
    }
}
