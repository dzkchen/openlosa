package app.openlosa.feed.dto;

import java.time.LocalDateTime;

/**
 * Liveness of the feed ingest loop, surfaced at {@code GET /api/v1/feed/health}.
 *
 * <p>{@code lastRun} is the most recent ingest attempt of any status (null before the
 * first run ever). {@code lastSuccessAt} is the {@code ranAt} of the most recent run
 * that actually applied a changed snapshot (null if the engine has never produced data
 * OpenLOSA could ingest). {@code openJobs} counts open, non-hidden feed jobs so the UI
 * can reassure the reader the feed still works even while it is stale. {@code stale} is
 * true when the effective freshness (last applied change or last confirmed-unchanged
 * snapshot) is missing or older than {@code staleAfterHours}.
 */
public record FeedHealthResponse(
    FeedIngestRunSummary lastRun,
    LocalDateTime lastSuccessAt,
    long openJobs,
    boolean stale,
    int staleAfterHours
) {
}
