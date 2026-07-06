package app.openlosa.outreach.dto;

import java.time.LocalDate;

import app.openlosa.outreach.OutreachStatus;
import app.openlosa.outreach.OutreachType;
import jakarta.validation.constraints.Size;

public record OutreachUpdateRequest(
    Long contactId,
    Boolean clearContact,
    Long companyId,
    @Size(max = 255) String companyName,
    @Size(max = 2048) String companyWebsite,
    String companyNotes,
    Boolean clearCompany,
    Long applicationId,
    Boolean clearApplication,
    OutreachType type,
    OutreachStatus status,
    LocalDate sentAt,
    Boolean clearSentAt,
    LocalDate followUpBy,
    Boolean clearFollowUpBy,
    String notes
) {
}
