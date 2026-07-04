package app.openlosa.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import app.openlosa.application.ApplicationSource;
import app.openlosa.application.ApplicationStatus;

public record ApplicationResponse(
    Long id,
    CompanyResponse company,
    String roleTitle,
    String postingUrl,
    String location,
    ApplicationStatus status,
    LocalDate appliedAt,
    ApplicationSource source,
    String salaryText,
    String notes,
    boolean favorite,
    List<TagResponse> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
