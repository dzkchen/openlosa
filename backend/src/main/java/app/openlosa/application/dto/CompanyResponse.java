package app.openlosa.application.dto;

import java.time.LocalDateTime;

public record CompanyResponse(
    Long id,
    String name,
    String website,
    String notes,
    LocalDateTime createdAt
) {
}
