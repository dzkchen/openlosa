package app.openlosa.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import app.openlosa.dashboard.dto.DashboardStatsResponse;

@Repository
class DashboardStatsRepository {

    private static final String STATS_SQL = """
        SELECT
            COUNT(CASE WHEN a.applied_at BETWEEN ? AND ? THEN 1 END) AS applications_last_7_days,
            COUNT(CASE WHEN a.applied_at BETWEEN ? AND ? THEN 1 END) AS applications_last_30_days,
            COUNT(CASE WHEN a.applied_at BETWEEN ? AND ? THEN 1 END) AS applications_last_60_days,
            COUNT(CASE
                WHEN a.status NOT IN ('REJECTED', 'WITHDRAWN', 'GHOSTED')
                 AND NOT EXISTS (
                     SELECT 1
                     FROM status_transition st
                     WHERE st.application_id = a.id
                       AND st.occurred_at >= ?
                 )
                THEN 1
            END) AS no_update,
            COUNT(CASE WHEN a.status = 'OFFER' THEN 1 END) AS offers,
            COUNT(CASE
                WHEN a.status NOT IN ('REJECTED', 'WITHDRAWN', 'GHOSTED') THEN 1
            END) AS ongoing
        FROM application a
        """;

    private final JdbcTemplate jdbcTemplate;

    DashboardStatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    DashboardStatsResponse getStats(LocalDate today, LocalDateTime noUpdateCutoff) {
        return jdbcTemplate.queryForObject(
            STATS_SQL,
            (resultSet, rowNumber) -> new DashboardStatsResponse(
                resultSet.getLong("applications_last_7_days"),
                resultSet.getLong("applications_last_30_days"),
                resultSet.getLong("applications_last_60_days"),
                resultSet.getLong("no_update"),
                resultSet.getLong("offers"),
                resultSet.getLong("ongoing")
            ),
            today.minusDays(6),
            today,
            today.minusDays(29),
            today,
            today.minusDays(59),
            today,
            noUpdateCutoff
        );
    }
}
