package app.openlosa.emailfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class EmailFinderIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailFinderSidecarClient sidecarClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM email_lookup");
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
    void lookupProxiesSidecarAndPersistsRankedCandidates() throws Exception {
        long contactId = createContact("""
            {
              "companyName": "OpenAI",
              "name": "Ada Lovelace"
            }
            """);
        mockSidecarResponse();

        String response = mockMvc.perform(post("/api/v1/email-finder/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "contactId": %d,
                      "personName": "Ada Lovelace",
                      "companyUrl": "https://openai.com",
                      "count": 2,
                      "noSmtp": true
                    }
                    """.formatted(contactId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.contactId", is((int) contactId)))
            .andExpect(jsonPath("$.domain", is("openai.com")))
            .andExpect(jsonPath("$.topStatus", is("VERIFIED")))
            .andExpect(jsonPath("$.topConfidence", is(96)))
            .andExpect(jsonPath("$.candidates", hasSize(2)))
            .andExpect(jsonPath("$.candidates[0].email", is("ada@openai.com")))
            .andExpect(jsonPath("$.candidates[0].status", is("VERIFIED")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        long lookupId = objectMapper.readTree(response).get("id").asLong();
        String persistedJson = jdbcTemplate.queryForObject(
            "SELECT candidates_json FROM email_lookup WHERE id = ?",
            String.class,
            lookupId
        );
        String topStatus = jdbcTemplate.queryForObject(
            "SELECT top_status FROM email_lookup WHERE id = ?",
            String.class,
            lookupId
        );

        assertThat(topStatus).isEqualTo("VERIFIED");
        assertThat(persistedJson).contains("ada@openai.com", "UNKNOWN");
    }

    @Test
    void chooseFillsContactEmailAndSeedsColdEmailOutreach() throws Exception {
        long contactId = createContact("""
            {
              "companyName": "OpenAI",
              "name": "Ada Lovelace"
            }
            """);
        mockSidecarResponse();
        long lookupId = lookup(contactId);

        mockMvc.perform(post("/api/v1/email-finder/{lookupId}/choose", lookupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "ada@openai.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lookupId", is((int) lookupId)))
            .andExpect(jsonPath("$.chosenEmail", is("ada@openai.com")))
            .andExpect(jsonPath("$.contactId", is((int) contactId)))
            .andExpect(jsonPath("$.outreachId", notNullValue()));

        String contactEmail = jdbcTemplate.queryForObject(
            "SELECT email FROM contact WHERE id = ?",
            String.class,
            contactId
        );
        Integer outreachCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outreach WHERE contact_id = ? AND type = 'COLD_EMAIL' AND status = 'TO_SEND'",
            Integer.class,
            contactId
        );
        String chosenEmail = jdbcTemplate.queryForObject(
            "SELECT chosen_email FROM email_lookup WHERE id = ?",
            String.class,
            lookupId
        );

        assertThat(contactEmail).isEqualTo("ada@openai.com");
        assertThat(chosenEmail).isEqualTo("ada@openai.com");
        assertThat(outreachCount).isOne();
    }

    @Test
    void repeatedChooseReturnsExistingSeededOutreach() throws Exception {
        long contactId = createContact("""
            {
              "name": "Ada Lovelace"
            }
            """);
        mockSidecarResponse();
        long lookupId = lookup(contactId);

        JsonNode first = choose(lookupId, "ada@openai.com");
        JsonNode second = choose(lookupId, "ada@openai.com");

        Integer outreachCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outreach WHERE contact_id = ?",
            Integer.class,
            contactId
        );

        assertThat(second.get("outreachId").asLong()).isEqualTo(first.get("outreachId").asLong());
        assertThat(outreachCount).isOne();
    }

    @Test
    void chooseEndpointBodyVariantRejectsEmailsOutsideCandidates() throws Exception {
        long contactId = createContact("""
            {
              "name": "Ada Lovelace"
            }
            """);
        mockSidecarResponse();
        long lookupId = lookup(contactId);

        mockMvc.perform(post("/api/v1/email-finder/choose")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lookupId": %d,
                      "email": "manual@openai.com"
                    }
                    """.formatted(lookupId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", is("email must match one of the lookup candidates")));

        Integer outreachCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outreach WHERE contact_id = ?",
            Integer.class,
            contactId
        );
        assertThat(outreachCount).isZero();
    }

    @Test
    void pathChooseRejectsConflictingBodyLookupId() throws Exception {
        long contactId = createContact("""
            {
              "name": "Ada Lovelace"
            }
            """);
        mockSidecarResponse();
        long lookupId = lookup(contactId);

        mockMvc.perform(post("/api/v1/email-finder/{lookupId}/choose", lookupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lookupId": %d,
                      "email": "ada@openai.com"
                    }
                    """.formatted(lookupId + 1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", is("lookupId in the request body must match the path")));
    }

    private void mockSidecarResponse() {
        when(sidecarClient.find(any())).thenReturn(new EmailFinderSidecarResponse(
            "openai.com",
            "OpenAI",
            2,
            3,
            List.of(
                new EmailFinderSidecarCandidate(
                    "Ada@OpenAI.com",
                    "Ada Lovelace",
                    "verified",
                    96,
                    1,
                    1,
                    "aspmx.l.google.com",
                    250,
                    120,
                    false
                ),
                new EmailFinderSidecarCandidate(
                    "ada.lovelace@openai.com",
                    "Ada Lovelace",
                    "unknown",
                    0,
                    2,
                    2,
                    null,
                    null,
                    null,
                    null
                )
            ),
            "2026-07-09T10:00:00Z"
        ));
    }

    private long lookup(long contactId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/email-finder/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "contactId": %d,
                      "personName": "Ada Lovelace",
                      "companyUrl": "https://openai.com",
                      "noSmtp": true
                    }
                    """.formatted(contactId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private JsonNode choose(long lookupId, String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/email-finder/{lookupId}/choose", lookupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response);
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
}
