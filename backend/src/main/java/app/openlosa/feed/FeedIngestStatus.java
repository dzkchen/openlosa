package app.openlosa.feed;

import java.util.List;

enum FeedIngestStatus {
    SUCCESS,
    SKIPPED,
    FAILED;

    // Runs that recorded a content fingerprint: the ingest either applied the snapshot
    // (SUCCESS) or confirmed it was byte-identical to current state (SKIPPED "unchanged").
    // Missing-file and lock-contention skips carry a null fingerprint and are excluded.
    // Shared by ingest change detection and /feed/health freshness so they cannot desync.
    static final List<FeedIngestStatus> FINGERPRINTED = List.of(SUCCESS, SKIPPED);
}
