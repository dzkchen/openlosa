package app.openlosa.application.dto;

import jakarta.validation.constraints.NotNull;

public record ApplicationFavoriteRequest(
    @NotNull Boolean favorite
) {
}
