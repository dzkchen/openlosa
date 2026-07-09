package app.openlosa.prospect.dto;

import java.time.LocalDateTime;
import java.util.List;

import app.openlosa.application.dto.TagResponse;
import app.openlosa.prospect.ProspectPriority;
import app.openlosa.prospect.ProspectStatus;

public record ProspectResponse(
    Long id,
    String name,
    String url,
    String note,
    ProspectPriority priority,
    ProspectStatus status,
    ProspectApplicationResponse promotedApplication,
    List<TagResponse> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
