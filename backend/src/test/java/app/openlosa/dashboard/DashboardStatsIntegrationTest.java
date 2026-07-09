package app.openlosa.dashboard;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardStatsIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM status_transition");
        jdbcTemplate.update("DELETE FROM application");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void returnsZeroesWhenThereAreNoApplications() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationsLast7Days", is(0)))
            .andExpect(jsonPath("$.applicationsLast30Days", is(0)))
            .andExpect(jsonPath("$.applicationsLast60Days", is(0)))
            .andExpect(jsonPath("$.noUpdate", is(0)))
            .andExpect(jsonPath("$.offers", is(0)))
            .andExpect(jsonPath("$.ongoing", is(0)));
    }

    @Test
    void aggregatesApplicationWindowsAndCurrentPipelineState() throws Exception {
        long companyId = createCompany();

        createApplication(companyId, "Recent applied", "APPLIED", 0, 0);
        createApplication(companyId, "Seven day boundary", "REJECTED", 6, 6);
        createApplication(companyId, "Stale interview", "INTERVIEW", 7, 15);
        createApplication(companyId, "Offer", "OFFER", 29, 20);
        createApplication(companyId, "Rejected", "REJECTED", 59, 30);
        createApplication(companyId, "Saved lead", "SAVED", null, 30);
        createApplication(companyId, "Old ghosted", "GHOSTED", 60, 60);
        createApplication(companyId, "Future dated", "REJECTED", -1, 0);

        mockMvc.perform(get("/api/v1/dashboard/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationsLast7Days", is(2)))
            .andExpect(jsonPath("$.applicationsLast30Days", is(4)))
            .andExpect(jsonPath("$.applicationsLast60Days", is(5)))
            .andExpect(jsonPath("$.noUpdate", is(3)))
            .andExpect(jsonPath("$.offers", is(1)))
            .andExpect(jsonPath("$.ongoing", is(4)));
    }

    private long createCompany() {
        jdbcTemplate.update("INSERT INTO company (name) VALUES ('Dashboard Test Company')");
        return jdbcTemplate.queryForObject(
            "SELECT id FROM company WHERE name = 'Dashboard Test Company'",
            Long.class
        );
    }

    private void createApplication(
        long companyId,
        String roleTitle,
        String status,
        Integer appliedDaysAgo,
        int transitionDaysAgo
    ) {
        LocalDate appliedAt = appliedDaysAgo == null ? null : LocalDate.now().minusDays(appliedDaysAgo);

        jdbcTemplate.update(
            """
            INSERT INTO application (company_id, role_title, status, applied_at, source, favorite)
            VALUES (?, ?, ?, ?, 'MANUAL', FALSE)
            """,
            companyId,
            roleTitle,
            status,
            appliedAt
        );
        Long applicationId = jdbcTemplate.queryForObject(
            "SELECT id FROM application WHERE role_title = ?",
            Long.class,
            roleTitle
        );
        jdbcTemplate.update(
            """
            INSERT INTO status_transition (application_id, from_status, to_status, occurred_at)
            VALUES (?, NULL, ?, ?)
            """,
            applicationId,
            status,
            LocalDateTime.now().minusDays(transitionDaysAgo)
        );
    }
}
