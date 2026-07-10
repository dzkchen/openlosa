package app.openlosa.feed;

import java.time.LocalDate;

record FeedJobSnapshot(
    String engineId,
    String companyName,
    String title,
    String url,
    String location,
    String sourceAts,
    String sponsorship,
    LocalDate postedAt
) {
}
