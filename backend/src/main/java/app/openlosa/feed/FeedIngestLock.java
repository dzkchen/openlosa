package app.openlosa.feed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class FeedIngestLock {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedIngestLock.class);
    private static final String LOCK_NAME = "openlosa.feed.ingest";

    private final DataSource dataSource;

    FeedIngestLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    Optional<Handle> tryAcquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, LOCK_NAME);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("GET_LOCK returned no row");
                    }
                    int acquired = result.getInt(1);
                    if (result.wasNull()) {
                        // NULL is an error, not contention; a skip would mask it.
                        throw new SQLException("GET_LOCK reported an error");
                    }
                    if (acquired == 1) {
                        return Optional.of(new Handle(connection));
                    }
                }
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException exception) {
            closeQuietly(connection);
            throw new IllegalStateException("Could not acquire the feed ingest lock", exception);
        }
    }

    final class Handle implements AutoCloseable {

        private final Connection connection;
        private boolean closed;

        private Handle(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, LOCK_NAME);
                statement.executeQuery();
            } catch (SQLException exception) {
                LOGGER.error("Could not release the feed ingest lock; discarding its connection", exception);
                // Returning the pooled connection would keep the session-scoped
                // lock held, so every later ingest would skip as contended.
                abortQuietly(connection);
                return;
            }
            closeQuietly(connection);
        }
    }

    private void abortQuietly(Connection connection) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException exception) {
            LOGGER.warn("Could not abort the feed ingest lock connection", exception);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            LOGGER.warn("Could not close the feed ingest lock connection", exception);
        }
    }
}
