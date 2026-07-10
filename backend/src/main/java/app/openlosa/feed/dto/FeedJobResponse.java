package app.openlosa.feed.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeedJobResponse(
    Long id,
    String companyName,
    String title,
    String url,
    String location,
    String sourceAts,
    String sponsorship,
    LocalDate postedAt,
    LocalDateTime firstSeenAt,
    LocalDateTime lastSeenAt,
    boolean open,
    boolean hidden,
    Long savedProspectId,
    Long createdApplicationId
) {
}
