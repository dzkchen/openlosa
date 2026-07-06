package app.openlosa.outreach.dto;

import java.time.LocalDate;

import app.openlosa.outreach.OutreachStatus;
import app.openlosa.outreach.OutreachType;
import jakarta.validation.constraints.Size;

public record OutreachCreateRequest(
    Long contactId,
    Long companyId,
    @Size(max = 255) String companyName,
    @Size(max = 2048) String companyWebsite,
    String companyNotes,
    Long applicationId,
    OutreachType type,
    OutreachStatus status,
    LocalDate sentAt,
    LocalDate followUpBy,
    String notes
) {
}
