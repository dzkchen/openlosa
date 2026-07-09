package app.openlosa.prospect.dto;

import java.util.List;

import app.openlosa.prospect.ProspectPriority;
import app.openlosa.prospect.ProspectStatus;
import jakarta.validation.constraints.Size;

public record ProspectUpdateRequest(
    @Size(max = 255) String name,
    @Size(max = 2048) String url,
    Boolean clearUrl,
    String note,
    Boolean clearNote,
    ProspectPriority priority,
    ProspectStatus status,
    List<Long> tagIds
) {
}
