package app.openlosa.feed;

import java.time.LocalDate;
import java.time.LocalDateTime;

import app.openlosa.application.JobApplication;
import app.openlosa.prospect.Prospect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "feed_job")
class FeedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "engine_id", nullable = false, unique = true)
    private String engineId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 1000)
    private String location;

    @Column(name = "source_ats", nullable = false, length = 80)
    private String sourceAts;

    @Column(length = 40)
    private String sponsorship;

    @Column(name = "posted_at")
    private LocalDate postedAt;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "missed_successful_ingests", nullable = false)
    private int missedSuccessfulIngests;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_prospect_id")
    private Prospect savedProspect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_application_id")
    private JobApplication createdApplication;

    protected FeedJob() {
    }

    FeedJob(FeedJobSnapshot snapshot, LocalDateTime seenAt) {
        this.engineId = snapshot.engineId();
        this.firstSeenAt = seenAt;
        this.hidden = false;
        applySnapshot(snapshot, seenAt);
    }

    String getEngineId() {
        return engineId;
    }

    boolean isOpen() {
        return open;
    }

    void applySnapshot(FeedJobSnapshot snapshot, LocalDateTime seenAt) {
        this.companyName = snapshot.companyName();
        this.title = snapshot.title();
        this.url = snapshot.url();
        this.location = snapshot.location();
        this.sourceAts = snapshot.sourceAts();
        this.sponsorship = snapshot.sponsorship();
        this.postedAt = snapshot.postedAt();
        this.lastSeenAt = seenAt;
        this.open = true;
        this.missedSuccessfulIngests = 0;
    }

    boolean recordSuccessfulMiss() {
        if (!open) {
            return false;
        }
        // The clamp is unreachable under normal flow; it keeps the
        // chk_feed_job_missed_ingests CHECK satisfiable if a row is corrupted.
        missedSuccessfulIngests = Math.min(2, missedSuccessfulIngests + 1);
        if (missedSuccessfulIngests < 2) {
            return false;
        }
        open = false;
        return true;
    }

    boolean closeExplicitly() {
        if (!open) {
            return false;
        }
        open = false;
        return true;
    }
}
