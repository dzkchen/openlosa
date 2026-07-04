package app.openlosa.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2048) String website,
    String notes
) {
}
