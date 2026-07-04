package app.openlosa.application.dto;

import java.util.List;

public record ApplicationImportResponse(
    int importedCount,
    List<ApplicationResponse> applications
) {
}
