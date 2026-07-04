package app.openlosa.contact.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import app.openlosa.application.dto.CompanyResponse;
import app.openlosa.contact.ContactRelationship;

public record ContactResponse(
    Long id,
    CompanyResponse company,
    String name,
    String title,
    String email,
    String linkedinUrl,
    ContactRelationship relationship,
    String notes,
    LocalDate lastContactedAt,
    LocalDateTime createdAt
) {
}
