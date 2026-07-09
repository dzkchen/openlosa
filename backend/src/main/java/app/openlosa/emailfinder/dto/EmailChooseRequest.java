package app.openlosa.emailfinder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChooseRequest(
    Long lookupId,
    Long contactId,
    @NotBlank @Email @Size(max = 255) String email,
    Boolean createOutreach
) {
}
