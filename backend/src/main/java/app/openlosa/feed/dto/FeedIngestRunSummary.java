package app.openlosa.feed.dto;

import java.time.LocalDateTime;

/**
 * A condensed view of a single feed ingest run for the health endpoint.
 * {@code status} is the {@code FeedIngestStatus} name (SUCCESS / SKIPPED / FAILED)
 * and {@code message} carries the skip/failure detail (null on a plain success).
 */
public record FeedIngestRunSummary(
    LocalDateTime ranAt,
    String status,
    String message,
    int jobsSeen,
    int jobsNew,
    int jobsClosed
) {
}
