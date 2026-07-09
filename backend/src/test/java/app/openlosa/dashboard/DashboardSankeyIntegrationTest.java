package app.openlosa.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class DashboardSankeyIntegrationTest {

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
    void returnsNoLinksWhenThereAreNoApplications() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/sankey"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("[]", true));
    }

    @Test
    void aggregatesRealTransitionsAndBalancingPseudoEdges() throws Exception {
        long companyId = createCompany();

        long phoneScreen = createApplication(companyId, "Active pipeline", "PHONE_SCREEN");
        addTransition(phoneScreen, null, "SAVED", 30);
        addTransition(phoneScreen, "SAVED", "APPLIED", 20);
        addTransition(phoneScreen, "APPLIED", "PHONE_SCREEN", 1);

        long staleApplied = createApplication(companyId, "Stale applied", "APPLIED");
        addTransition(staleApplied, null, "APPLIED", 20);

        long activeApplied = createApplication(companyId, "Active applied", "APPLIED");
        addTransition(activeApplied, null, "APPLIED", 1);

        long rejected = createApplication(companyId, "Rejected", "REJECTED");
        addTransition(rejected, null, "SAVED", 30);
        addTransition(rejected, "SAVED", "REJECTED", 20);

        long staleOffer = createApplication(companyId, "Stale offer", "OFFER");
        addTransition(staleOffer, null, "OFFER", 20);

        mockMvc.perform(get("/api/v1/dashboard/sankey"))
            .andExpect(status().isOk())
            .andExpect(content().json(
                """
                [
                  {"from":"Applications","to":"Applied","count":2},
                  {"from":"Applications","to":"Offer","count":1},
                  {"from":"Applications","to":"Saved","count":2},
                  {"from":"Applied","to":"In Progress","count":1},
                  {"from":"Applied","to":"No Update","count":1},
                  {"from":"Applied","to":"Phone Screen","count":1},
                  {"from":"Offer","to":"No Update","count":1},
                  {"from":"Phone Screen","to":"In Progress","count":1},
                  {"from":"Saved","to":"Applied","count":1},
                  {"from":"Saved","to":"Rejected","count":1}
                ]
                """,
                true
            ));
    }

    private long createCompany() {
        jdbcTemplate.update("INSERT INTO company (name) VALUES ('Sankey Test Company')");
        return jdbcTemplate.queryForObject(
            "SELECT id FROM company WHERE name = 'Sankey Test Company'",
            Long.class
        );
    }

    private long createApplication(long companyId, String roleTitle, String status) {
        jdbcTemplate.update(
            """
            INSERT INTO application (company_id, role_title, status, source, favorite)
            VALUES (?, ?, ?, 'MANUAL', FALSE)
            """,
            companyId,
            roleTitle,
            status
        );
        return jdbcTemplate.queryForObject(
            "SELECT id FROM application WHERE role_title = ?",
            Long.class,
            roleTitle
        );
    }

    private void addTransition(
        long applicationId,
        String fromStatus,
        String toStatus,
        int daysAgo
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO status_transition (application_id, from_status, to_status, occurred_at)
            VALUES (?, ?, ?, ?)
            """,
            applicationId,
            fromStatus,
            toStatus,
            LocalDateTime.now().minusDays(daysAgo)
        );
    }
}
