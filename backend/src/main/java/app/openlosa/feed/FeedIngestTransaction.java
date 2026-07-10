package app.openlosa.feed;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FeedIngestTransaction {

    private static final List<FeedIngestStatus> PROCESSED_STATUSES = List.of(
        FeedIngestStatus.SUCCESS,
        FeedIngestStatus.SKIPPED
    );

    private final FeedJobRepository feedJobRepository;
    private final FeedIngestRunRepository runRepository;
    private final Clock clock;

    @Autowired
    FeedIngestTransaction(
        FeedJobRepository feedJobRepository,
        FeedIngestRunRepository runRepository
    ) {
        this(feedJobRepository, runRepository, Clock.systemUTC());
    }

    FeedIngestTransaction(
        FeedJobRepository feedJobRepository,
        FeedIngestRunRepository runRepository,
        Clock clock
    ) {
        this.feedJobRepository = feedJobRepository;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional
    FeedIngestRun applyIfChanged(ParsedFeedSnapshot snapshot) {
        LocalDateTime ranAt = LocalDateTime.now(clock);
        Set<String> incomingEngineIds = new HashSet<>();
        for (FeedJobSnapshot job : snapshot.jobs()) {
            incomingEngineIds.add(job.engineId());
        }
        // Skip only when the currently-open set is exactly the incoming open
        // set: every incoming job is already open (nothing to open/reopen) AND
        // no open job is absent from the feed's open set (nothing pending an
        // explicit close or a closing strike). Requiring set equality rather
        // than containsAll means an open-but-absent job still advances toward
        // closing even if the engine re-emits a byte-identical jobs.json, so a
        // deterministic feed cannot strand a dead posting open forever.
        boolean openSetUnchanged = feedJobRepository.findOpenEngineIds()
            .equals(incomingEngineIds);
        boolean sameFingerprint = runRepository
            .findFirstByFileFingerprintIsNotNullAndStatusInOrderByIdDesc(PROCESSED_STATUSES)
            .map(FeedIngestRun::getFileFingerprint)
            .filter(snapshot.fingerprint()::equals)
            .isPresent();
        if (openSetUnchanged && sameFingerprint) {
            return runRepository.save(FeedIngestRun.skipped(
                ranAt,
                "jobs.json is unchanged",
                snapshot.fingerprint(),
                snapshot.fileModifiedAt()
            ));
        }

        List<FeedJob> jobs = incomingEngineIds.isEmpty()
            ? feedJobRepository.findByOpenTrue()
            : feedJobRepository.findByOpenTrueOrEngineIdIn(incomingEngineIds);
        Map<String, FeedJob> jobsByEngineId = new HashMap<>();
        for (FeedJob job : jobs) {
            jobsByEngineId.put(job.getEngineId(), job);
        }

        List<FeedJob> newJobs = new ArrayList<>();
        for (FeedJobSnapshot incoming : snapshot.jobs()) {
            FeedJob job = jobsByEngineId.get(incoming.engineId());
            if (job == null) {
                newJobs.add(new FeedJob(incoming, ranAt));
            } else {
                job.applySnapshot(incoming, ranAt);
            }
        }

        int jobsClosed = 0;
        for (FeedJob job : jobs) {
            if (incomingEngineIds.contains(job.getEngineId())) {
                continue;
            }
            boolean closed = snapshot.closedEngineIds().contains(job.getEngineId())
                ? job.closeExplicitly()
                : job.recordSuccessfulMiss();
            if (closed) {
                jobsClosed++;
            }
        }

        // Loaded entities are managed, so dirty checking flushes their updates
        // at commit; only new rows need an explicit save.
        feedJobRepository.saveAll(newJobs);
        return runRepository.save(FeedIngestRun.success(
            ranAt,
            snapshot.jobs().size(),
            newJobs.size(),
            jobsClosed,
            snapshot.fingerprint(),
            snapshot.fileModifiedAt()
        ));
    }
}
