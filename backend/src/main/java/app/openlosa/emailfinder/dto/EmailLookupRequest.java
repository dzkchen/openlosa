package app.openlosa.emailfinder.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailLookupRequest(
    Long contactId,
    @NotBlank @Size(max = 255) String personName,
    @NotBlank @Size(max = 2048) String companyUrl,
    @Min(1) @Max(20) Integer count,
    Boolean includeCatchAll,
    Boolean includeUnknown,
    Boolean noSmtp,
    @DecimalMin("0.0") @DecimalMax("5.0") Double delaySeconds
) {
}
