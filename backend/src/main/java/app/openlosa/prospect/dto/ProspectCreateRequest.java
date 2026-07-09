package app.openlosa.prospect.dto;

import java.util.List;

import app.openlosa.prospect.ProspectPriority;
import app.openlosa.prospect.ProspectStatus;
import jakarta.validation.constraints.Size;

public record ProspectCreateRequest(
    @Size(max = 255) String name,
    @Size(max = 2048) String url,
    String note,
    ProspectPriority priority,
    ProspectStatus status,
    List<Long> tagIds
) {
}
