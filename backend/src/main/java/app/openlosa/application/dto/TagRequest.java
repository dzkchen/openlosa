package app.openlosa.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
    @NotBlank @Size(max = 80) String name,
    @Size(max = 40) String color
) {
}
