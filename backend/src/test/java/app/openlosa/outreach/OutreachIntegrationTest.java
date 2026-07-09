package app.openlosa.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
class OutreachIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM outreach");
        jdbcTemplate.update("DELETE FROM prospect_tag");
        jdbcTemplate.update("DELETE FROM prospect");
        jdbcTemplate.update("DELETE FROM contact");
        jdbcTemplate.update("DELETE FROM application_tag");
        jdbcTemplate.update("DELETE FROM status_transition");
        jdbcTemplate.update("DELETE FROM application");
        jdbcTemplate.update("DELETE FROM tag");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void outreachCrudSupportsStatusFlowFollowUpAndContactLastContacted() throws Exception {
        long contactId = createContact("""
            {
              "companyName": "OpenAI",
              "name": "Ada Lovelace",
              "email": "ada@openai.com",
              "relationship": "RECRUITER"
            }
            """);

        long outreachId = createOutreach("""
            {
              "contactId": %d,
              "type": "COLD_EMAIL",
              "followUpBy": "2026-07-10",
              "notes": "Ask about internship openings"
            }
            """.formatted(contactId));

        mockMvc.perform(get("/api/v1/outreach/{id}", outreachId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contact.name", is("Ada Lovelace")))
            .andExpect(jsonPath("$.company.name", is("OpenAI")))
            .andExpect(jsonPath("$.status", is("TO_SEND")))
            .andExpect(jsonPath("$.sentAt", nullValue()))
            .andExpect(jsonPath("$.followUpBy", is("2026-07-10")));

        mockMvc.perform(put("/api/v1/outreach/{id}", outreachId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "REPLIED"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", is("Invalid outreach status transition TO_SEND -> REPLIED")));

        mockMvc.perform(put("/api/v1/outreach/{id}", outreachId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "SENT",
                      "sentAt": "2026-07-04"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("SENT")))
            .andExpect(jsonPath("$.sentAt", is("2026-07-04")));

        LocalDate lastContactedAt = jdbcTemplate.queryForObject(
            "SELECT last_contacted_at FROM contact WHERE id = ?",
            LocalDate.class,
            contactId
        );
        assertThat(lastContactedAt).isEqualTo(LocalDate.parse("2026-07-04"));

        mockMvc.perform(put("/api/v1/outreach/{id}", outreachId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "REPLIED",
                      "notes": "They responded with next steps"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("REPLIED")))
            .andExpect(jsonPath("$.notes", is("They responded with next steps")));

        mockMvc.perform(get("/api/v1/outreach")
                .param("status", "REPLIED")
                .param("q", "next steps")
                .param("followUpFrom", "2026-07-01")
                .param("followUpTo", "2026-07-31")
                .param("sort", "followUpBy")
                .param("dir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) outreachId)));

        mockMvc.perform(delete("/api/v1/outreach/{id}", outreachId))
            .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outreach WHERE id = ?",
            Integer.class,
            outreachId
        );
        assertThat(remaining).isZero();
    }

    @Test
    void sentOutreachDoesNotMoveContactLastContactedBackward() throws Exception {
        long contactId = createContact("""
            {
              "name": "Grace Hopper",
              "lastContactedAt": "2026-07-20"
            }
            """);

        createOutreach("""
            {
              "contactId": %d,
              "status": "SENT",
              "sentAt": "2026-07-04"
            }
            """.formatted(contactId));

        LocalDate lastContactedAt = jdbcTemplate.queryForObject(
            "SELECT last_contacted_at FROM contact WHERE id = ?",
            LocalDate.class,
            contactId
        );
        assertThat(lastContactedAt).isEqualTo(LocalDate.parse("2026-07-20"));
    }

    @Test
    void assigningContactToAlreadySentOutreachTouchesContactLastContacted() throws Exception {
        long contactId = createContact("""
            {
              "name": "Katherine Johnson"
            }
            """);

        long outreachId = createOutreach("""
            {
              "status": "SENT",
              "sentAt": "2026-07-04"
            }
            """);

        mockMvc.perform(put("/api/v1/outreach/{id}", outreachId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "contactId": %d
                    }
                    """.formatted(contactId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contact.name", is("Katherine Johnson")))
            .andExpect(jsonPath("$.sentAt", is("2026-07-04")));

        LocalDate lastContactedAt = jdbcTemplate.queryForObject(
            "SELECT last_contacted_at FROM contact WHERE id = ?",
            LocalDate.class,
            contactId
        );
        assertThat(lastContactedAt).isEqualTo(LocalDate.parse("2026-07-04"));
    }

    @Test
    void clearSentAtWithSentStatusIsRejected() throws Exception {
        long outreachId = createOutreach("""
            {
              "status": "SENT",
              "sentAt": "2026-07-04"
            }
            """);

        mockMvc.perform(put("/api/v1/outreach/{id}", outreachId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "SENT",
                      "clearSentAt": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", is("sentAt is required once outreach has been sent")));

        mockMvc.perform(get("/api/v1/outreach/{id}", outreachId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sentAt", is("2026-07-04")));
    }

    @Test
    void dueOutreachReturnsUnsentAndFollowUpsDueToday() throws Exception {
        var yesterday = LocalDate.now().minusDays(1).toString();
        var tomorrow = LocalDate.now().plusDays(1).toString();

        long unsentId = createOutreach("""
            {
              "notes": "Draft message"
            }
            """);
        long followUpId = createOutreach("""
            {
              "status": "SENT",
              "sentAt": "%s",
              "followUpBy": "%s",
              "notes": "Check for response"
            }
            """.formatted(yesterday, yesterday));
        createOutreach("""
            {
              "status": "SENT",
              "sentAt": "%s",
              "followUpBy": "%s",
              "notes": "Not due yet"
            }
            """.formatted(yesterday, tomorrow));

        mockMvc.perform(get("/api/v1/outreach/due"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", is((int) followUpId)))
            .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.containsInAnyOrder((int) unsentId, (int) followUpId)));
    }

    private long createContact(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private long createOutreach(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/outreach")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }
}
