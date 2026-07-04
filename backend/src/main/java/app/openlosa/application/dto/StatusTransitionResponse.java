package app.openlosa.application.dto;

import java.time.LocalDateTime;

import app.openlosa.application.ApplicationStatus;

public record StatusTransitionResponse(
    Long id,
    Long applicationId,
    ApplicationStatus fromStatus,
    ApplicationStatus toStatus,
    LocalDateTime occurredAt
) {
}
