package app.openlosa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApplicationTrackerIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM status_transition");
        jdbcTemplate.update("DELETE FROM application");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void applicationCrudSupportsFiltersAndSorts() throws Exception {
        long firstCompanyId = createCompany("Linear", "https://linear.app");
        long secondCompanyId = createCompany("Stripe", "https://stripe.com");

        long firstApplicationId = createApplication("""
            {
              "companyId": %d,
              "roleTitle": "Product Engineering Intern",
              "status": "SAVED",
              "source": "MANUAL",
              "favorite": true
            }
            """.formatted(firstCompanyId));
        createApplication("""
            {
              "companyId": %d,
              "roleTitle": "Backend Intern",
              "status": "APPLIED",
              "source": "FEED",
              "favorite": false
            }
            """.formatted(secondCompanyId));

        mockMvc.perform(get("/api/v1/applications")
                .param("favorite", "true")
                .param("sort", "company")
                .param("dir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].company.name", is("Linear")))
            .andExpect(jsonPath("$[0].roleTitle", is("Product Engineering Intern")));

        mockMvc.perform(put("/api/v1/applications/{id}", firstApplicationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "companyId": %d,
                      "roleTitle": "New Grad Engineer",
                      "postingUrl": "https://linear.app/careers/new-grad",
                      "location": "Remote",
                      "status": "SAVED",
                      "source": "MANUAL",
                      "salaryText": "$120k",
                      "notes": "Updated from tracker",
                      "favorite": false
                    }
                    """.formatted(firstCompanyId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roleTitle", is("New Grad Engineer")))
            .andExpect(jsonPath("$.favorite", is(false)));

        mockMvc.perform(get("/api/v1/applications")
                .param("q", "updated")
                .param("status", "SAVED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) firstApplicationId)));

        mockMvc.perform(delete("/api/v1/applications/{id}", firstApplicationId))
            .andExpect(status().isNoContent());

        Integer remainingApplications = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM application", Integer.class);
        assertThat(remainingApplications).isEqualTo(1);
    }

    @Test
    void creationWritesInitialTransitionAndStatusChangeAutoFillsAppliedAt() throws Exception {
        long applicationId = createApplication("""
            {
              "companyName": "OpenAI",
              "companyWebsite": "https://openai.com",
              "roleTitle": "Software Engineer Intern",
              "status": "SAVED",
              "source": "MANUAL"
            }
            """);

        Integer initialTransitions = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM status_transition
            WHERE application_id = ?
              AND from_status IS NULL
              AND to_status = 'SAVED'
            """,
            Integer.class,
            applicationId
        );
        assertThat(initialTransitions).isEqualTo(1);

        mockMvc.perform(post("/api/v1/applications/{id}/status", applicationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toStatus": "APPLIED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("APPLIED")))
            .andExpect(jsonPath("$.appliedAt", is(LocalDate.now().toString())));

        String persistedStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM application WHERE id = ?",
            String.class,
            applicationId
        );
        LocalDate appliedAt = jdbcTemplate.queryForObject(
            "SELECT applied_at FROM application WHERE id = ?",
            LocalDate.class,
            applicationId
        );
        Integer transitions = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM status_transition
            WHERE application_id = ?
            """,
            Integer.class,
            applicationId
        );
        Integer appliedTransition = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM status_transition
            WHERE application_id = ?
              AND from_status = 'SAVED'
              AND to_status = 'APPLIED'
            """,
            Integer.class,
            applicationId
        );

        assertThat(persistedStatus).isEqualTo("APPLIED");
        assertThat(appliedAt).isEqualTo(LocalDate.now());
        assertThat(transitions).isEqualTo(2);
        assertThat(appliedTransition).isEqualTo(1);
    }

    private long createCompany(String name, String website) throws Exception {
        var response = mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "website": "%s"
                    }
                    """.formatted(name, website)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return readId(response);
    }

    private long createApplication(String json) throws Exception {
        var response = mockMvc.perform(post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return readId(response);
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }
}
