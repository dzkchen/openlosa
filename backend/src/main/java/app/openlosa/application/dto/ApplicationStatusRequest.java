package app.openlosa.application.dto;

import app.openlosa.application.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusRequest(
    @NotNull ApplicationStatus toStatus
) {
}
