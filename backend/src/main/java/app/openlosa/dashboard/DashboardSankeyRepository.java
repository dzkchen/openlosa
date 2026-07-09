package app.openlosa.dashboard;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DashboardSankeyRepository {

    private static final String SANKEY_SQL = """
        SELECT edge_from, edge_to, COUNT(*) AS edge_count
        FROM (
            SELECT
                COALESCE(st.from_status, 'APPLICATIONS') AS edge_from,
                st.to_status AS edge_to
            FROM status_transition st

            UNION ALL

            SELECT
                a.status AS edge_from,
                CASE
                    WHEN NOT EXISTS (
                        SELECT 1
                        FROM status_transition recent
                        WHERE recent.application_id = a.id
                          AND recent.occurred_at >= ?
                    )
                    THEN 'NO_UPDATE'
                    ELSE 'IN_PROGRESS'
                END AS edge_to
            FROM application a
            WHERE a.status NOT IN ('REJECTED', 'WITHDRAWN', 'GHOSTED')
        ) edges
        GROUP BY edge_from, edge_to
        ORDER BY edge_from, edge_to
        """;

    private final JdbcTemplate jdbcTemplate;

    DashboardSankeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<SankeyEdge> getLinks(LocalDateTime noUpdateCutoff) {
        return jdbcTemplate.query(
            SANKEY_SQL,
            (resultSet, rowNumber) -> new SankeyEdge(
                resultSet.getString("edge_from"),
                resultSet.getString("edge_to"),
                resultSet.getLong("edge_count")
            ),
            noUpdateCutoff
        );
    }

    record SankeyEdge(String from, String to, long count) {
    }
}
