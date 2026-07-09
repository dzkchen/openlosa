package app.openlosa.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ContactIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM contact");
        jdbcTemplate.update("DELETE FROM application_tag");
        jdbcTemplate.update("DELETE FROM status_transition");
        jdbcTemplate.update("DELETE FROM application");
        jdbcTemplate.update("DELETE FROM tag");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void contactListPreservesUnpagedContractAndValidatesPagination() throws Exception {
        for (int index = 0; index < 101; index += 1) {
            jdbcTemplate.update("INSERT INTO contact (name) VALUES (?)", "Contact " + index);
        }

        mockMvc.perform(get("/api/v1/contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(101)));

        mockMvc.perform(get("/api/v1/contacts")
                .param("page", "0")
                .param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(100)));

        mockMvc.perform(get("/api/v1/contacts").param("page", "-1"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/contacts").param("size", "101"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void contactCrudSupportsImplicitCompaniesFiltersAndSorts() throws Exception {
        long contactId = createContact("""
            {
              "companyName": "OpenAI",
              "companyWebsite": "https://openai.com",
              "name": "Ada Lovelace",
              "title": "University Recruiter",
              "email": "ada@openai.com",
              "linkedinUrl": "https://linkedin.com/in/ada",
              "relationship": "RECRUITER",
              "notes": "Met at career fair",
              "lastContactedAt": "2026-02-01"
            }
            """);
        createContact("""
            {
              "companyName": "Stripe",
              "name": "Grace Hopper",
              "title": "Engineer",
              "relationship": "ALUM"
            }
            """);

        mockMvc.perform(get("/api/v1/contacts")
                .param("relationship", "RECRUITER")
                .param("company", "open")
                .param("contactedFrom", "2026-01-01")
                .param("contactedTo", "2026-02-28")
                .param("sort", "company")
                .param("dir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) contactId)))
            .andExpect(jsonPath("$[0].company.name", is("OpenAI")))
            .andExpect(jsonPath("$[0].name", is("Ada Lovelace")));

        mockMvc.perform(patch("/api/v1/contacts/{id}", contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Recruiting Lead",
                      "notes": "Follow up about internship roles"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Recruiting Lead")))
            .andExpect(jsonPath("$.notes", is("Follow up about internship roles")))
            .andExpect(jsonPath("$.email", is("ada@openai.com")))
            .andExpect(jsonPath("$.lastContactedAt", is("2026-02-01")));

        mockMvc.perform(get("/api/v1/contacts")
                .param("q", "follow up"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) contactId)));

        mockMvc.perform(delete("/api/v1/contacts/{id}", contactId))
            .andExpect(status().isNoContent());

        Integer remainingContacts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM contact WHERE id = ?",
            Integer.class,
            contactId
        );
        assertThat(remainingContacts).isZero();
    }

    @Test
    void contactCanBeStandaloneAndUpdateCanClearNullableFields() throws Exception {
        long contactId = createContact("""
            {
              "name": "No Company Person",
              "email": "person@example.com",
              "relationship": "OTHER",
              "lastContactedAt": "2026-03-04"
            }
            """);

        mockMvc.perform(get("/api/v1/contacts/{id}", contactId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.company", nullValue()))
            .andExpect(jsonPath("$.relationship", is("OTHER")));

        mockMvc.perform(put("/api/v1/contacts/{id}", contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "",
                      "clearLastContactedAt": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", nullValue()))
            .andExpect(jsonPath("$.lastContactedAt", nullValue()));

        mockMvc.perform(patch("/api/v1/contacts/{id}", contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "new@example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("new@example.com")));

        mockMvc.perform(patch("/api/v1/contacts/{id}", contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clearEmail": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", nullValue()));
    }

    @Test
    void deletingCompanyDetachesLinkedContacts() throws Exception {
        long contactId = createContact("""
            {
              "companyName": "OpenAI",
              "name": "Ada Lovelace"
            }
            """);
        Long companyId = jdbcTemplate.queryForObject(
            "SELECT company_id FROM contact WHERE id = ?",
            Long.class,
            contactId
        );

        mockMvc.perform(delete("/api/v1/companies/{id}", companyId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/contacts/{id}", contactId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.company", nullValue()));
    }

    @Test
    void updateRejectsClearingAndSettingDateTogether() throws Exception {
        long contactId = createContact("""
            {
              "name": "Ada Lovelace"
            }
            """);

        mockMvc.perform(put("/api/v1/contacts/{id}", contactId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastContactedAt": "2026-03-04",
                      "clearLastContactedAt": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", is("lastContactedAt cannot be set and cleared in the same request")));
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
