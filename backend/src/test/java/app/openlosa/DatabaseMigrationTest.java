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

        Integer companyTables = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'company'",
            Integer.class
        );
        Integer applicationTables = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'application'",
            Integer.class
        );
        Integer flywayMigrations = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
            Integer.class
        );

        assertThat(companyTables).isEqualTo(1);
        assertThat(applicationTables).isEqualTo(1);
        assertThat(flywayMigrations).isGreaterThanOrEqualTo(1);
    }
}
