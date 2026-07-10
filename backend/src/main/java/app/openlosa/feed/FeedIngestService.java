package app.openlosa.feed;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class FeedIngestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedIngestService.class);

    private final Path jobsFile;
    private final FeedJobsFileParser parser;
    private final FeedIngestTransaction ingestTransaction;
    private final FeedIngestRunRepository runRepository;
    private final FeedIngestLock ingestLock;
    private final Clock clock;

    @Autowired
    FeedIngestService(
        @Value("${openlosa.engine.jobs-file}") String jobsFile,
        FeedJobsFileParser parser,
        FeedIngestTransaction ingestTransaction,
        FeedIngestRunRepository runRepository,
        FeedIngestLock ingestLock
    ) {
        this(
            Path.of(jobsFile),
            parser,
            ingestTransaction,
            runRepository,
            ingestLock,
            Clock.systemUTC()
        );
    }

    FeedIngestService(
        Path jobsFile,
        FeedJobsFileParser parser,
        FeedIngestTransaction ingestTransaction,
        FeedIngestRunRepository runRepository,
        FeedIngestLock ingestLock,
        Clock clock
    ) {
        this.jobsFile = jobsFile;
        this.parser = parser;
        this.ingestTransaction = ingestTransaction;
        this.runRepository = runRepository;
        this.ingestLock = ingestLock;
        this.clock = clock;
        LOGGER.info("Feed ingest reads jobs from {}", this.jobsFile.toAbsolutePath());
    }

    FeedIngestRun runOnce() {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        try {
            var lock = ingestLock.tryAcquire();
            if (lock.isEmpty()) {
                return runRepository.save(FeedIngestRun.skipped(
                    startedAt,
                    "another feed ingest is already running",
                    null,
                    null
                ));
            }
            // Parsing inside the lock keeps a concurrent instance from applying
            // an older snapshot over a newer one after the file is replaced.
            try (var ignored = lock.get()) {
                if (!Files.isRegularFile(jobsFile)) {
                    return recordMissingFile(startedAt);
                }
                ParsedFeedSnapshot snapshot = parser.parse(jobsFile);
                FeedIngestRun run = ingestTransaction.applyIfChanged(snapshot);
                LOGGER.info(
                    "Feed ingest finished with status {} and {} open jobs",
                    run.getStatus(),
                    snapshot.jobs().size()
                );
                return run;
            }
        } catch (FeedIngestException | RuntimeException exception) {
            if (exception.getCause() instanceof NoSuchFileException) {
                return recordMissingFile(startedAt);
            }
            String message = failureMessage(exception);
            LOGGER.error("Feed ingest failed: {}", message, exception);
            return runRepository.save(FeedIngestRun.failed(startedAt, message));
        }
    }

    private FeedIngestRun recordMissingFile(LocalDateTime startedAt) {
        return runRepository.save(FeedIngestRun.skipped(
            startedAt,
            "jobs.json is missing at " + jobsFile.toAbsolutePath(),
            null,
            null
        ));
    }

    private String failureMessage(Exception exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + detail;
    }
}
