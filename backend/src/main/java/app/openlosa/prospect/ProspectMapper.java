package app.openlosa.prospect;

import app.openlosa.application.Tag;
import app.openlosa.application.dto.TagResponse;
import app.openlosa.prospect.dto.ProspectApplicationResponse;
import app.openlosa.prospect.dto.ProspectResponse;

final class ProspectMapper {

    private ProspectMapper() {
    }

    static ProspectResponse toResponse(Prospect prospect) {
        var promotedApplication = prospect.getPromotedApplication() == null
            ? null
            : new ProspectApplicationResponse(
                prospect.getPromotedApplication().getId(),
                prospect.getPromotedApplication().getRoleTitle(),
                prospect.getPromotedApplication().getCompany().getName()
            );

        return new ProspectResponse(
            prospect.getId(),
            prospect.getName(),
            prospect.getUrl(),
            prospect.getNote(),
            prospect.getPriority(),
            prospect.getStatus(),
            promotedApplication,
            prospect.getTags().stream()
                .map(ProspectMapper::toResponse)
                .toList(),
            prospect.getCreatedAt(),
            prospect.getUpdatedAt()
        );
    }

    private static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor());
    }
}
