package app.openlosa;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliesCurrentSchema() {
        var jdbcTemplate = new JdbcTemplate(dataSource);

        Integer tables = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                  'company',
                  'application',
                  'status_transition',
                  'tag',
                  'application_tag',
                  'contact',
                  'outreach',
                  'prospect',
                  'prospect_tag',
                  'email_lookup'
              )
            """,
            Integer.class
        );
        Integer applicationTagIndexes = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'application_tag'
              AND index_name = 'ix_application_tag_tag_id'
            """,
            Integer.class
        );
        Integer requiredFlywayMigrations = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE success = 1
              AND version IN ('1', '2', '3', '4', '5', '6')
            """,
            Integer.class
        );

        Integer outreachIndexes = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'outreach'
              AND index_name IN ('ix_outreach_contact_id', 'ix_outreach_status', 'ix_outreach_follow_up_by')
            """,
            Integer.class
        );
        Integer prospectIndexes = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'prospect'
              AND index_name IN ('ix_prospect_status', 'ix_prospect_priority', 'ix_prospect_created_at')
            """,
            Integer.class
        );
        Integer emailLookupIndexes = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'email_lookup'
              AND index_name IN (
                  'ix_email_lookup_contact_id',
                  'ix_email_lookup_chosen_outreach_id',
                  'ix_email_lookup_created_at'
              )
            """,
            Integer.class
        );

        assertThat(tables).isEqualTo(10);
        assertThat(applicationTagIndexes).isEqualTo(1);
        assertThat(outreachIndexes).isEqualTo(3);
        assertThat(prospectIndexes).isEqualTo(3);
        assertThat(emailLookupIndexes).isEqualTo(3);
        assertThat(requiredFlywayMigrations).isEqualTo(6);
    }
}
