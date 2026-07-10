package app.openlosa.feed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "openlosa.feed.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true
)
class FeedIngestScheduler {

    private final FeedIngestService ingestService;

    FeedIngestScheduler(FeedIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Scheduled(
        fixedDelayString = "${openlosa.feed.ingest-interval}",
        initialDelayString = "${openlosa.feed.ingest-initial-delay}"
    )
    void ingest() {
        ingestService.runOnce();
    }
}
