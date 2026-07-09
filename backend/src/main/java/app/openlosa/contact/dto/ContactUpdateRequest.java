package app.openlosa.contact.dto;

import java.time.LocalDate;

import app.openlosa.contact.ContactRelationship;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ContactUpdateRequest(
    Long companyId,
    @Size(max = 255) String companyName,
    @Size(max = 2048) String companyWebsite,
    String companyNotes,
    Boolean clearCompany,
    @Size(max = 255) String name,
    @Size(max = 255) String title,
    @Email @Size(max = 255) String email,
    Boolean clearEmail,
    @Size(max = 2048) String linkedinUrl,
    ContactRelationship relationship,
    String notes,
    LocalDate lastContactedAt,
    Boolean clearLastContactedAt
) {
}
