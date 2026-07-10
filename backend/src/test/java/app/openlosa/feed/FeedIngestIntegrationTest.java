package app.openlosa.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "openlosa.feed.scheduling-enabled=false")
@Testcontainers
class FeedIngestIntegrationTest {

    private static final Path JOBS_FILE = createJobsFilePath();

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void feedProperties(DynamicPropertyRegistry registry) {
        registry.add("openlosa.engine.jobs-file", () -> JOBS_FILE.toString());
    }

    @Autowired
    private FeedIngestService ingestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDatabaseAndFile() throws IOException {
        jdbcTemplate.update("DELETE FROM feed_ingest_run");
        jdbcTemplate.update("DELETE FROM feed_job");
        Files.deleteIfExists(JOBS_FILE);
    }

    @Test
    void upsertsOpenEngineJobsAndSkipsAnUnchangedFile() throws IOException {
        writeJobs(snapshot("Platform Intern", true));

        FeedIngestRun firstRun = ingestService.runOnce();
        FeedIngestRun unchangedRun = ingestService.runOnce();

        assertThat(firstRun.getStatus()).isEqualTo(FeedIngestStatus.SUCCESS);
        assertThat(unchangedRun.getStatus()).isEqualTo(FeedIngestStatus.SKIPPED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_job", Integer.class))
            .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed_job WHERE engine_id = 'ashby:oldco:closed'",
            Integer.class
        )).isZero();

        Map<String, Object> alpha = jdbcTemplate.queryForMap(
            "SELECT * FROM feed_job WHERE engine_id = 'greenhouse:alpha:123'"
        );
        assertThat(alpha)
            .containsEntry("company_name", "Alpha Labs")
            .containsEntry("title", "Platform Intern")
            .containsEntry("source_ats", "greenhouse")
            .containsEntry("sponsorship", "offers");
        assertThat(alpha.get("posted_at").toString()).isEqualTo("2026-07-08");
        assertThat(((Number) alpha.get("missed_successful_ingests")).intValue()).isZero();
        assertThat((Boolean) alpha.get("is_open")).isTrue();

        Map<String, Object> firstRunRow = jdbcTemplate.queryForMap(
            "SELECT jobs_seen, jobs_new, jobs_closed FROM feed_ingest_run WHERE status = 'SUCCESS'"
        );
        assertThat(firstRunRow)
            .containsEntry("jobs_seen", 2)
            .containsEntry("jobs_new", 2)
            .containsEntry("jobs_closed", 0);

        jdbcTemplate.update("DELETE FROM feed_job");
        FeedIngestRun rebuiltRun = ingestService.runOnce();
        assertThat(rebuiltRun.getStatus()).isEqualTo(FeedIngestStatus.SUCCESS);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_job", Integer.class))
            .isEqualTo(2);
    }

    @Test
    void closesAfterTwoSuccessiveAbsencesEvenWhenTheFileIsByteStableAndReopensOnReturn()
        throws IOException {
        writeJobs(snapshot("Platform Intern v1", true));
        ingestService.runOnce();

        // Beta disappears (first strike); alpha's title also changes so this
        // file differs from v1.
        writeJobs(snapshot("Platform Intern v2", false));
        ingestService.runOnce();
        assertBetaState(true, 1);

        // The engine re-emits a byte-identical file while beta is still absent.
        // The absence must still land the second strike and close beta, so a
        // deterministic engine that never mutates jobs.json between cycles
        // cannot strand a dead posting open forever.
        FeedIngestRun stableAbsence = ingestService.runOnce();
        assertThat(stableAbsence.getStatus()).isEqualTo(FeedIngestStatus.SUCCESS);
        assertBetaState(false, 2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT jobs_closed FROM feed_ingest_run ORDER BY id DESC LIMIT 1",
            Integer.class
        )).isEqualTo(1);

        // Once beta has closed, the same byte-identical file has nothing left to
        // apply, so the run skips again.
        FeedIngestRun afterClose = ingestService.runOnce();
        assertThat(afterClose.getStatus()).isEqualTo(FeedIngestStatus.SKIPPED);
        assertBetaState(false, 2);

        writeJobs(snapshot("Platform Intern v4", true));
        ingestService.runOnce();
        assertBetaState(true, 0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT jobs_new FROM feed_ingest_run ORDER BY id DESC LIMIT 1",
            Integer.class
        )).isZero();
    }

    @Test
    void failedParseRecordsFailureWithoutApplyingAClosingStrike() throws IOException {
        writeJobs(snapshot("Platform Intern", true));
        ingestService.runOnce();

        writeJobs("""
            {
              "greenhouse:alpha:123": {
                "id": "greenhouse:alpha:123",
                "is_open": true
              }
            }
            """);
        FeedIngestRun failedRun = ingestService.runOnce();

        assertThat(failedRun.getStatus()).isEqualTo(FeedIngestStatus.FAILED);
        assertBetaState(true, 0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT message FROM feed_ingest_run WHERE status = 'FAILED'",
            String.class
        )).contains("company");
    }

    @Test
    void skipsWhileAnotherSessionHoldsTheIngestLock() throws Exception {
        writeJobs(snapshot("Platform Intern v1", true));
        ingestService.runOnce();
        writeJobs(snapshot("Platform Intern v2", false));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                "SELECT GET_LOCK('openlosa.feed.ingest', 0)"
            )) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }

            FeedIngestRun contendedRun = ingestService.runOnce();
            assertThat(contendedRun.getStatus()).isEqualTo(FeedIngestStatus.SKIPPED);
            assertBetaState(true, 0);

            statement.execute("SELECT RELEASE_LOCK('openlosa.feed.ingest')");
        }

        FeedIngestRun appliedRun = ingestService.runOnce();
        assertThat(appliedRun.getStatus()).isEqualTo(FeedIngestStatus.SUCCESS);
        assertBetaState(true, 1);
    }

    @Test
    void closesImmediatelyWhenTheFeedMarksAPostingClosed() throws IOException {
        writeJobs(snapshot("Platform Intern", true));
        ingestService.runOnce();

        writeJobs(snapshot("Platform Intern", "false"));
        FeedIngestRun closingRun = ingestService.runOnce();

        assertThat(closingRun.getStatus()).isEqualTo(FeedIngestStatus.SUCCESS);
        assertBetaState(false, 0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT jobs_closed FROM feed_ingest_run ORDER BY id DESC LIMIT 1",
            Integer.class
        )).isEqualTo(1);
    }

    @Test
    void missingFileRecordsSkipWithoutChangingJobs() throws IOException {
        writeJobs(snapshot("Platform Intern", true));
        ingestService.runOnce();
        Files.delete(JOBS_FILE);

        FeedIngestRun skippedRun = ingestService.runOnce();

        assertThat(skippedRun.getStatus()).isEqualTo(FeedIngestStatus.SKIPPED);
        assertBetaState(true, 0);
    }

    private void assertBetaState(boolean open, int missedIngests) {
        Map<String, Object> beta = jdbcTemplate.queryForMap(
            """
            SELECT is_open, missed_successful_ingests
            FROM feed_job
            WHERE engine_id = 'lever:beta:456'
            """
        );
        assertThat((Boolean) beta.get("is_open")).isEqualTo(open);
        assertThat(((Number) beta.get("missed_successful_ingests")).intValue())
            .isEqualTo(missedIngests);
    }

    private void writeJobs(String json) throws IOException {
        Files.writeString(JOBS_FILE, json);
    }

    private String snapshot(String alphaTitle, boolean includeBeta) {
        return snapshot(alphaTitle, includeBeta ? "true" : null);
    }

    private String snapshot(String alphaTitle, String betaIsOpen) {
        String beta = betaIsOpen != null
            ? """
                ,
                "lever:beta:456": {
                  "id": "lever:beta:456",
                  "company": "Beta Systems",
                  "title": "Data Engineering Intern",
                  "url": "https://beta.example/jobs/456",
                  "location": "Remote",
                  "source": "lever",
                  "sponsorship": "unknown",
                  "posted_at": null,
                  "is_open": %s
                }
                """.formatted(betaIsOpen)
            : "";
        return """
            {
              "greenhouse:alpha:123": {
                "id": "greenhouse:alpha:123",
                "company": "Alpha Labs",
                "title": "%s",
                "url": "https://alpha.example/jobs/123",
                "location": "New York, NY",
                "source": "greenhouse",
                "sponsorship": "offers",
                "posted_at": "2026-07-08T00:00:00Z",
                "is_open": true
              }%s,
              "ashby:oldco:closed": {
                "id": "ashby:oldco:closed",
                "company": "Old Co",
                "title": "Closed Intern",
                "url": "https://old.example/jobs/closed",
                "source": "ashby",
                "is_open": false
              }
            }
            """.formatted(alphaTitle, beta);
    }

    private static Path createJobsFilePath() {
        try {
            return Files.createTempDirectory("openlosa-feed-test").resolve("jobs.json");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
