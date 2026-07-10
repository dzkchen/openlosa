package app.openlosa.feed;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "feed_ingest_run")
class FeedIngestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ran_at", nullable = false, updatable = false)
    private LocalDateTime ranAt;

    @Column(name = "jobs_seen", nullable = false)
    private int jobsSeen;

    @Column(name = "jobs_new", nullable = false)
    private int jobsNew;

    @Column(name = "jobs_closed", nullable = false)
    private int jobsClosed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedIngestStatus status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "file_fingerprint", length = 64)
    private String fileFingerprint;

    @Column(name = "file_modified_at")
    private LocalDateTime fileModifiedAt;

    protected FeedIngestRun() {
    }

    private FeedIngestRun(
        LocalDateTime ranAt,
        int jobsSeen,
        int jobsNew,
        int jobsClosed,
        FeedIngestStatus status,
        String message,
        String fileFingerprint,
        LocalDateTime fileModifiedAt
    ) {
        this.ranAt = ranAt;
        this.jobsSeen = jobsSeen;
        this.jobsNew = jobsNew;
        this.jobsClosed = jobsClosed;
        this.status = status;
        this.message = message;
        this.fileFingerprint = fileFingerprint;
        this.fileModifiedAt = fileModifiedAt;
    }

    static FeedIngestRun success(
        LocalDateTime ranAt,
        int jobsSeen,
        int jobsNew,
        int jobsClosed,
        String fingerprint,
        LocalDateTime fileModifiedAt
    ) {
        return new FeedIngestRun(
            ranAt,
            jobsSeen,
            jobsNew,
            jobsClosed,
            FeedIngestStatus.SUCCESS,
            null,
            fingerprint,
            fileModifiedAt
        );
    }

    static FeedIngestRun skipped(
        LocalDateTime ranAt,
        String message,
        String fingerprint,
        LocalDateTime fileModifiedAt
    ) {
        return new FeedIngestRun(
            ranAt,
            0,
            0,
            0,
            FeedIngestStatus.SKIPPED,
            message,
            fingerprint,
            fileModifiedAt
        );
    }

    static FeedIngestRun failed(LocalDateTime ranAt, String message) {
        return new FeedIngestRun(
            ranAt,
            0,
            0,
            0,
            FeedIngestStatus.FAILED,
            message,
            null,
            null
        );
    }

    LocalDateTime getRanAt() {
        return ranAt;
    }

    int getJobsSeen() {
        return jobsSeen;
    }

    int getJobsNew() {
        return jobsNew;
    }

    int getJobsClosed() {
        return jobsClosed;
    }

    FeedIngestStatus getStatus() {
        return status;
    }

    String getMessage() {
        return message;
    }

    String getFileFingerprint() {
        return fileFingerprint;
    }
}
