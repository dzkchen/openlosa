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
    void flywayAppliesBaselineSchema() {
        var jdbcTemplate = new JdbcTemplate(dataSource);

        Integer baselineTables = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('company', 'application', 'status_transition', 'tag', 'application_tag')
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
        Integer flywayMigrations = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
            Integer.class
        );

        assertThat(baselineTables).isEqualTo(5);
        assertThat(applicationTagIndexes).isEqualTo(1);
        assertThat(flywayMigrations).isEqualTo(1);
    }
}
