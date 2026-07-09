package app.openlosa.prospect;

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
class ProspectIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM prospect_tag");
        jdbcTemplate.update("DELETE FROM prospect");
        jdbcTemplate.update("DELETE FROM outreach");
        jdbcTemplate.update("DELETE FROM contact");
        jdbcTemplate.update("DELETE FROM application_tag");
        jdbcTemplate.update("DELETE FROM status_transition");
        jdbcTemplate.update("DELETE FROM application");
        jdbcTemplate.update("DELETE FROM tag");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void prospectCrudSupportsFiltersPartialUpdatesAndTags() throws Exception {
        long firstTagId = createTag("Remote", "#60a5fa");
        long secondTagId = createTag("AI", "#a78bfa");
        long prospectId = createProspect("""
            {
              "name": "OpenAI internships",
              "url": "https://openai.com/careers",
              "note": "Watch emerging roles",
              "priority": "HIGH",
              "tagIds": [%d, %d]
            }
            """.formatted(firstTagId, secondTagId));
        createProspect("""
            {
              "name": "Later company",
              "priority": "LOW",
              "status": "DROPPED"
            }
            """);

        mockMvc.perform(get("/api/v1/prospects")
                .param("sort", "createdAt")
                .param("dir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", is((int) prospectId)));

        mockMvc.perform(get("/api/v1/prospects")
                .param("priority", "HIGH")
                .param("status", "NEW")
                .param("tagId", String.valueOf(firstTagId))
                .param("q", "emerging")
                .param("sort", "name")
                .param("dir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) prospectId)))
            .andExpect(jsonPath("$[0].name", is("OpenAI internships")))
            .andExpect(jsonPath("$[0].priority", is("HIGH")))
            .andExpect(jsonPath("$[0].status", is("NEW")))
            .andExpect(jsonPath("$[0].tags", hasSize(2)));

        mockMvc.perform(put("/api/v1/prospects/{id}", prospectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "note": "Research recruiter names",
                      "status": "RESEARCHING",
                      "tagIds": [%d]
                    }
                    """.formatted(secondTagId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url", is("https://openai.com/careers")))
            .andExpect(jsonPath("$.note", is("Research recruiter names")))
            .andExpect(jsonPath("$.status", is("RESEARCHING")))
            .andExpect(jsonPath("$.tags", hasSize(1)))
            .andExpect(jsonPath("$.tags[0].name", is("AI")));

        mockMvc.perform(put("/api/v1/prospects/{id}", prospectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clearUrl": true,
                      "clearNote": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url", nullValue()))
            .andExpect(jsonPath("$.note", nullValue()));

        mockMvc.perform(delete("/api/v1/prospects/{id}", prospectId))
            .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM prospect WHERE id = ?",
            Integer.class,
            prospectId
        );
        assertThat(remaining).isZero();
    }

    @Test
    void updateRejectsClearingAndSettingUrlTogether() throws Exception {
        long prospectId = createProspect("""
            {
              "name": "Conflicting update"
            }
            """);

        mockMvc.perform(put("/api/v1/prospects/{id}", prospectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "url": "https://example.com",
                      "clearUrl": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", is("url cannot be set and cleared in the same request")));
    }

    private long createTag(String name, String color) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "color": "%s"
                    }
                    """.formatted(name, color)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private long createProspect(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/prospects")
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
