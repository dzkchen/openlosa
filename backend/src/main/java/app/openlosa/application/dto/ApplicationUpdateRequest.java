package app.openlosa.application.dto;

import java.time.LocalDate;

import app.openlosa.application.ApplicationSource;
import app.openlosa.application.ApplicationStatus;
import jakarta.validation.constraints.Size;

public record ApplicationUpdateRequest(
    Long companyId,
    @Size(max = 255) String companyName,
    @Size(max = 2048) String companyWebsite,
    String companyNotes,
    @Size(max = 255) String roleTitle,
    @Size(max = 2048) String postingUrl,
    @Size(max = 255) String location,
    ApplicationStatus status,
    LocalDate appliedAt,
    Boolean clearAppliedAt,
    ApplicationSource source,
    @Size(max = 255) String salaryText,
    String notes,
    Boolean favorite
) {
}
