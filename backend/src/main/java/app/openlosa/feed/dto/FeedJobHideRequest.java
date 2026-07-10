package app.openlosa.feed.dto;

import jakarta.validation.constraints.NotNull;

public record FeedJobHideRequest(
    @NotNull Boolean hidden
) {
}
