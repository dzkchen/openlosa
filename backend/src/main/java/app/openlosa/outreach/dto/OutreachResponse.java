package app.openlosa.outreach.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import app.openlosa.application.dto.CompanyResponse;
import app.openlosa.contact.dto.ContactResponse;
import app.openlosa.outreach.OutreachStatus;
import app.openlosa.outreach.OutreachType;

public record OutreachResponse(
    Long id,
    ContactResponse contact,
    CompanyResponse company,
    OutreachApplicationResponse application,
    OutreachType type,
    OutreachStatus status,
    LocalDate sentAt,
    LocalDate followUpBy,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
