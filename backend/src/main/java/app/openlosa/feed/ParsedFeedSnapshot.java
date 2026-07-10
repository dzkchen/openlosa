package app.openlosa.feed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

record ParsedFeedSnapshot(
    String fingerprint,
    LocalDateTime fileModifiedAt,
    List<FeedJobSnapshot> jobs,
    Set<String> closedEngineIds
) {
}
