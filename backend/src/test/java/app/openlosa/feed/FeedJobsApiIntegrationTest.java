package app.openlosa.feed;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FeedJobsApiIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM feed_job");
        jdbcTemplate.update("DELETE FROM status_transition");
        jdbcTemplate.update("DELETE FROM prospect");
        jdbcTemplate.update("DELETE FROM application");
        jdbcTemplate.update("DELETE FROM company");
        insert("greenhouse:acme:1", "Acme Corp", "Backend Intern", "H1B", LocalDate.parse("2026-06-01"), true, false);
        insert("greenhouse:beta:2", "Beta Labs", "Frontend Intern", "NONE", LocalDate.parse("2026-06-05"), true, false);
        insert("ashby:gamma:3", "Gamma Inc", "Data Engineer", "H1B", LocalDate.parse("2026-06-03"), false, false);
        insert("lever:delta:4", "Delta Co", "ML Intern", "NONE", LocalDate.parse("2026-06-10"), true, true);
        insert("ashby:epsilon:5", "Epsilon", "Backend Engineer", null, null, true, false);
    }

    @Test
    void listExcludesHiddenAndSortsByPostedDateDescWithNullsLast() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(4)))
            .andExpect(jsonPath("$.totalPages", is(1)))
            .andExpect(jsonPath("$.page", is(0)))
            .andExpect(jsonPath("$.size", is(25)))
            .andExpect(jsonPath("$.content", hasSize(4)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Beta Labs", "Gamma Inc", "Acme Corp", "Epsilon")))
            .andExpect(jsonPath("$.content[0].sponsorship", is("NONE")))
            .andExpect(jsonPath("$.content[3].postedAt").doesNotExist());
    }

    @Test
    void paginationSplitsPagesAndHandlesOutOfRangePage() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("size", "2").param("page", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(4)))
            .andExpect(jsonPath("$.totalPages", is(2)))
            .andExpect(jsonPath("$.size", is(2)))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Beta Labs", "Gamma Inc")));

        mockMvc.perform(get("/api/v1/feed/jobs").param("size", "2").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Acme Corp", "Epsilon")));

        mockMvc.perform(get("/api/v1/feed/jobs").param("size", "2").param("page", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", is(5)))
            .andExpect(jsonPath("$.totalElements", is(4)))
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void sizeIsClampedToMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("size", "5000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size", is(100)));
    }

    @Test
    void queryFilterMatchesCompanyOrTitleCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("q", "intern"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(2)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Beta Labs", "Acme Corp")));

        mockMvc.perform(get("/api/v1/feed/jobs").param("q", "GAMMA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(1)))
            .andExpect(jsonPath("$.content[0].companyName", is("Gamma Inc")));
    }

    @Test
    void sponsorshipFilterMatchesExactValue() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("sponsorship", "H1B"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(2)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Gamma Inc", "Acme Corp")));
    }

    @Test
    void openFilterSelectsOpenOrClosed() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("open", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(1)))
            .andExpect(jsonPath("$.content[0].companyName", is("Gamma Inc")))
            .andExpect(jsonPath("$.content[0].open", is(false)));

        mockMvc.perform(get("/api/v1/feed/jobs").param("open", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    void postedDateRangeFiltersInclusive() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs")
                .param("postedFrom", "2026-06-03")
                .param("postedTo", "2026-06-05"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(2)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Beta Labs", "Gamma Inc")));
    }

    @Test
    void hiddenTrueRevealsHiddenAndVisibleRows() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("hidden", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(5)))
            .andExpect(jsonPath("$.content[0].companyName", is("Delta Co")))
            .andExpect(jsonPath("$.content[0].hidden", is(true)));
    }

    @Test
    void unknownSortFallsBackToDefaultWithoutError() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("sort", "notacolumn").param("dir", "sideways"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(4)))
            .andExpect(jsonPath("$.content[*].companyName", contains("Beta Labs", "Gamma Inc", "Acme Corp", "Epsilon")));
    }

    @Test
    void whitelistedSortByCompanyAscending() throws Exception {
        mockMvc.perform(get("/api/v1/feed/jobs").param("sort", "company").param("dir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].companyName", contains("Acme Corp", "Beta Labs", "Epsilon", "Gamma Inc")));
    }

    @Test
    void hideAndUnhideRoundTrip() throws Exception {
        long acmeId = idOf("greenhouse:acme:1");

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/hide", acmeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is((int) acmeId)))
            .andExpect(jsonPath("$.hidden", is(true)));

        mockMvc.perform(get("/api/v1/feed/jobs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(3)));

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/hide", acmeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hidden", is(false)));

        mockMvc.perform(get("/api/v1/feed/jobs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(4)));
    }

    @Test
    void hideRejectsMissingHiddenField() throws Exception {
        long acmeId = idOf("greenhouse:acme:1");
        mockMvc.perform(post("/api/v1/feed/jobs/{id}/hide", acmeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void hideUnknownJobReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/feed/jobs/{id}/hide", 999999)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\": true}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail", is("Feed job 999999 was not found")));
    }

    @Test
    void saveProspectCreatesPrefilledProspectAndLinksIt() throws Exception {
        long acmeId = idOf("greenhouse:acme:1");

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/save-prospect", acmeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is((int) acmeId)))
            .andExpect(jsonPath("$.savedProspectId").isNumber())
            .andExpect(jsonPath("$.createdApplicationId").doesNotExist());

        var prospect = jdbcTemplate.queryForMap("SELECT * FROM prospect");
        org.junit.jupiter.api.Assertions.assertEquals("Acme Corp — Backend Intern", prospect.get("name"));
        org.junit.jupiter.api.Assertions.assertEquals("https://example.com/greenhouse:acme:1", prospect.get("url"));
        org.junit.jupiter.api.Assertions.assertEquals(
            "Saved from feed via greenhouse. Location: Remote.", prospect.get("note"));
        org.junit.jupiter.api.Assertions.assertEquals("NEW", prospect.get("status"));
        org.junit.jupiter.api.Assertions.assertEquals("MEDIUM", prospect.get("priority"));

        Long linkedProspectId = jdbcTemplate.queryForObject(
            "SELECT saved_prospect_id FROM feed_job WHERE id = ?", Long.class, acmeId);
        org.junit.jupiter.api.Assertions.assertEquals(prospect.get("id"), linkedProspectId);
    }

    @Test
    void createApplicationCreatesSavedFeedApplicationWithInitialTransition() throws Exception {
        long acmeId = idOf("greenhouse:acme:1");

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/create-application", acmeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is((int) acmeId)))
            .andExpect(jsonPath("$.createdApplicationId").isNumber())
            .andExpect(jsonPath("$.savedProspectId").doesNotExist());

        var application = jdbcTemplate.queryForMap("SELECT * FROM application");
        org.junit.jupiter.api.Assertions.assertEquals("SAVED", application.get("status"));
        org.junit.jupiter.api.Assertions.assertEquals("FEED", application.get("source"));
        org.junit.jupiter.api.Assertions.assertEquals("Backend Intern", application.get("role_title"));
        org.junit.jupiter.api.Assertions.assertEquals(
            "https://example.com/greenhouse:acme:1", application.get("posting_url"));
        org.junit.jupiter.api.Assertions.assertEquals("Remote", application.get("location"));

        String companyName = jdbcTemplate.queryForObject(
            "SELECT name FROM company WHERE id = ?", String.class, application.get("company_id"));
        org.junit.jupiter.api.Assertions.assertEquals("Acme Corp", companyName);

        var transitions = jdbcTemplate.queryForList(
            "SELECT from_status, to_status FROM status_transition WHERE application_id = ?",
            application.get("id"));
        org.junit.jupiter.api.Assertions.assertEquals(1, transitions.size());
        org.junit.jupiter.api.Assertions.assertNull(transitions.get(0).get("from_status"));
        org.junit.jupiter.api.Assertions.assertEquals("SAVED", transitions.get(0).get("to_status"));

        Long linkedApplicationId = jdbcTemplate.queryForObject(
            "SELECT created_application_id FROM feed_job WHERE id = ?", Long.class, acmeId);
        org.junit.jupiter.api.Assertions.assertEquals(application.get("id"), linkedApplicationId);
    }

    @Test
    void saveProspectIsIdempotentOnRepeatedCall() throws Exception {
        long acmeId = idOf("greenhouse:acme:1");

        var first = mockMvc.perform(post("/api/v1/feed/jobs/{id}/save-prospect", acmeId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var second = mockMvc.perform(post("/api/v1/feed/jobs/{id}/save-prospect", acmeId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        Integer prospectCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prospect", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, prospectCount);
    }

    @Test
    void createApplicationIsIdempotentOnRepeatedCall() throws Exception {
        long acmeId = idOf("greenhouse:acme:1");

        var first = mockMvc.perform(post("/api/v1/feed/jobs/{id}/create-application", acmeId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var second = mockMvc.perform(post("/api/v1/feed/jobs/{id}/create-application", acmeId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        Integer applicationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM application", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, applicationCount);
        Integer transitionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM status_transition", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, transitionCount);
    }

    @Test
    void actionsWorkOnClosedAndHiddenJobs() throws Exception {
        long gammaId = idOf("ashby:gamma:3");
        long deltaId = idOf("lever:delta:4");

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/create-application", gammaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdApplicationId").isNumber());
        mockMvc.perform(post("/api/v1/feed/jobs/{id}/save-prospect", deltaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedProspectId").isNumber());
    }

    @Test
    void saveProspectUnknownJobReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/feed/jobs/{id}/save-prospect", 999999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail", is("Feed job 999999 was not found")));
    }

    @Test
    void createApplicationUnknownJobReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/feed/jobs/{id}/create-application", 999999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail", is("Feed job 999999 was not found")));
    }

    @Test
    void saveProspectTruncatesNameTooLongForProspectColumn() throws Exception {
        insertWithText("greenhouse:longtitle:9", "Acme Corp", "T".repeat(400), "Remote");
        long id = idOf("greenhouse:longtitle:9");

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/save-prospect", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedProspectId").isNumber());

        String name = jdbcTemplate.queryForObject("SELECT name FROM prospect", String.class);
        org.junit.jupiter.api.Assertions.assertTrue(
            name.length() <= 255, "prospect name should be truncated to the column limit");
        org.junit.jupiter.api.Assertions.assertTrue(
            name.startsWith("Acme Corp — "), "truncated name should keep the composed prefix");
    }

    @Test
    void createApplicationTruncatesTitleAndLocationTooLongForColumns() throws Exception {
        insertWithText("greenhouse:longboth:9", "Acme Corp", "R".repeat(400), "L".repeat(400));
        long id = idOf("greenhouse:longboth:9");

        mockMvc.perform(post("/api/v1/feed/jobs/{id}/create-application", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdApplicationId").isNumber());

        var application = jdbcTemplate.queryForMap("SELECT role_title, location FROM application");
        org.junit.jupiter.api.Assertions.assertTrue(
            ((String) application.get("role_title")).length() <= 255, "role_title should be truncated");
        org.junit.jupiter.api.Assertions.assertTrue(
            ((String) application.get("location")).length() <= 255, "location should be truncated");
    }

    private void insert(
        String engineId,
        String company,
        String title,
        String sponsorship,
        LocalDate postedAt,
        boolean open,
        boolean hidden
    ) {
        var now = LocalDateTime.of(2026, 6, 11, 8, 0);
        jdbcTemplate.update("""
            INSERT INTO feed_job
                (engine_id, company_name, title, url, location, source_ats, sponsorship,
                 posted_at, first_seen_at, last_seen_at, is_open, hidden, missed_successful_ingests)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            engineId,
            company,
            title,
            "https://example.com/" + engineId,
            "Remote",
            engineId.split(":")[0],
            sponsorship,
            postedAt,
            now,
            now,
            open,
            hidden
        );
    }

    private void insertWithText(String engineId, String company, String title, String location) {
        var now = LocalDateTime.of(2026, 6, 11, 8, 0);
        jdbcTemplate.update("""
            INSERT INTO feed_job
                (engine_id, company_name, title, url, location, source_ats, sponsorship,
                 posted_at, first_seen_at, last_seen_at, is_open, hidden, missed_successful_ingests)
            VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, TRUE, FALSE, 0)
            """,
            engineId,
            company,
            title,
            "https://example.com/" + engineId,
            location,
            engineId.split(":")[0],
            now,
            now
        );
    }

    private long idOf(String engineId) {
        return jdbcTemplate.queryForObject("SELECT id FROM feed_job WHERE engine_id = ?", Long.class, engineId);
    }
}
