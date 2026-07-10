package app.openlosa.feed;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Covers {@code GET /api/v1/feed/health}. The default stale window is 24h; runs are
 * inserted with explicit ran_at timestamps relative to {@code now} so the staleness
 * boundary is exercised without waiting on the clock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FeedHealthApiIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM feed_ingest_run");
        jdbcTemplate.update("DELETE FROM feed_job");
    }

    @Test
    void noRunsYetReportsStaleWithNullTimestamps() throws Exception {
        mockMvc.perform(get("/api/v1/feed/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stale", is(true)))
            .andExpect(jsonPath("$.staleAfterHours", is(24)))
            .andExpect(jsonPath("$.openJobs", is(0)))
            .andExpect(jsonPath("$.lastRun", nullValue()))
            .andExpect(jsonPath("$.lastSuccessAt", nullValue()));
    }

    @Test
    void freshSuccessReportsHealthyWithLastRunAndOpenJobCount() throws Exception {
        insertJob("greenhouse:acme:1", true, false);
        insertJob("greenhouse:beta:2", true, false);
        insertJob("ashby:gamma:3", false, false); // closed, excluded from openJobs
        insertJob("lever:delta:4", true, true); // hidden, excluded from openJobs
        insertSuccess(LocalDateTime.now(ZoneOffset.UTC).minusHours(1), 4, 2, 1, "fp-fresh");

        mockMvc.perform(get("/api/v1/feed/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stale", is(false)))
            .andExpect(jsonPath("$.openJobs", is(2)))
            .andExpect(jsonPath("$.lastSuccessAt").isNotEmpty())
            .andExpect(jsonPath("$.lastRun.status", is("SUCCESS")))
            .andExpect(jsonPath("$.lastRun.jobsSeen", is(4)))
            .andExpect(jsonPath("$.lastRun.jobsNew", is(2)))
            .andExpect(jsonPath("$.lastRun.jobsClosed", is(1)));
    }

    @Test
    void successOlderThanThresholdReportsStaleButKeepsLastSuccess() throws Exception {
        insertSuccess(LocalDateTime.now(ZoneOffset.UTC).minusHours(30), 3, 0, 0, "fp-old");

        mockMvc.perform(get("/api/v1/feed/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stale", is(true)))
            .andExpect(jsonPath("$.lastSuccessAt").isNotEmpty())
            .andExpect(jsonPath("$.lastRun.status", is("SUCCESS")));
    }

    @Test
    void recentUnchangedSkipKeepsFeedFreshEvenWhenSuccessIsOld() throws Exception {
        // Success was long ago, but the engine keeps re-emitting a byte-identical file
        // that the ingest confirms unchanged (fingerprint recorded) — feed is fresh-in-effect.
        insertSuccess(LocalDateTime.now(ZoneOffset.UTC).minusHours(48), 3, 0, 0, "fp-stable");
        insertSkipped(LocalDateTime.now(ZoneOffset.UTC).minusHours(1), "jobs.json is unchanged", "fp-stable");

        mockMvc.perform(get("/api/v1/feed/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stale", is(false)))
            .andExpect(jsonPath("$.lastRun.status", is("SKIPPED")));
    }

    @Test
    void recentMissingFileSkipDoesNotCountAsFresh() throws Exception {
        // Missing-file / lock skips carry a null fingerprint and are not evidence of
        // fresh engine data, so an old success still leaves the feed stale.
        insertSuccess(LocalDateTime.now(ZoneOffset.UTC).minusHours(48), 3, 0, 0, "fp-old");
        insertSkipped(LocalDateTime.now(ZoneOffset.UTC).minusHours(1), "jobs.json is missing at /tmp/jobs.json", null);

        mockMvc.perform(get("/api/v1/feed/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stale", is(true)))
            .andExpect(jsonPath("$.lastRun.status", is("SKIPPED")));
    }

    @Test
    void failureAfterFreshSuccessSurfacesFailureButStaysWithinWindow() throws Exception {
        insertSuccess(LocalDateTime.now(ZoneOffset.UTC).minusHours(2), 5, 1, 0, "fp-ok");
        insertFailed(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30), "IOException: engine unreachable");

        mockMvc.perform(get("/api/v1/feed/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stale", is(false)))
            .andExpect(jsonPath("$.lastSuccessAt").isNotEmpty())
            .andExpect(jsonPath("$.lastRun.status", is("FAILED")))
            .andExpect(jsonPath("$.lastRun.message", is("IOException: engine unreachable")));
    }

    private void insertJob(String engineId, boolean open, boolean hidden) {
        var now = LocalDateTime.of(2026, 6, 11, 8, 0);
        jdbcTemplate.update("""
            INSERT INTO feed_job
                (engine_id, company_name, title, url, location, source_ats, sponsorship,
                 posted_at, first_seen_at, last_seen_at, is_open, hidden, missed_successful_ingests)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            engineId,
            "Company " + engineId,
            "Intern",
            "https://example.com/" + engineId,
            "Remote",
            engineId.split(":")[0],
            null,
            LocalDate.parse("2026-06-01"),
            now,
            now,
            open,
            hidden
        );
    }

    private void insertSuccess(
        LocalDateTime ranAt,
        int jobsSeen,
        int jobsNew,
        int jobsClosed,
        String fingerprint
    ) {
        jdbcTemplate.update("""
            INSERT INTO feed_ingest_run
                (ran_at, jobs_seen, jobs_new, jobs_closed, status, message, file_fingerprint, file_modified_at)
            VALUES (?, ?, ?, ?, 'SUCCESS', NULL, ?, ?)
            """,
            ranAt, jobsSeen, jobsNew, jobsClosed, fingerprint, ranAt);
    }

    private void insertSkipped(LocalDateTime ranAt, String message, String fingerprint) {
        jdbcTemplate.update("""
            INSERT INTO feed_ingest_run
                (ran_at, jobs_seen, jobs_new, jobs_closed, status, message, file_fingerprint, file_modified_at)
            VALUES (?, 0, 0, 0, 'SKIPPED', ?, ?, ?)
            """,
            ranAt, message, fingerprint, fingerprint == null ? null : ranAt);
    }

    private void insertFailed(LocalDateTime ranAt, String message) {
        jdbcTemplate.update("""
            INSERT INTO feed_ingest_run
                (ran_at, jobs_seen, jobs_new, jobs_closed, status, message, file_fingerprint, file_modified_at)
            VALUES (?, 0, 0, 0, 'FAILED', ?, NULL, NULL)
            """,
            ranAt, message);
    }
}
