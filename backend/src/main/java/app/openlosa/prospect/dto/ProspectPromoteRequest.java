package app.openlosa.prospect.dto;

import jakarta.validation.constraints.Size;

public record ProspectPromoteRequest(
    @Size(max = 255) String companyName,
    @Size(max = 255) String roleTitle
) {
}
