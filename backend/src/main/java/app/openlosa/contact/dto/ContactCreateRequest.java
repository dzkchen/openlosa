package app.openlosa.contact.dto;

import java.time.LocalDate;

import app.openlosa.contact.ContactRelationship;
import jakarta.validation.constraints.Size;

public record ContactCreateRequest(
    Long companyId,
    @Size(max = 255) String companyName,
    @Size(max = 2048) String companyWebsite,
    String companyNotes,
    @Size(max = 255) String name,
    @Size(max = 255) String title,
    @Size(max = 255) String email,
    @Size(max = 2048) String linkedinUrl,
    ContactRelationship relationship,
    String notes,
    LocalDate lastContactedAt
) {
}
