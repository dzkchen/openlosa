package app.openlosa.application;

import app.openlosa.application.dto.ApplicationResponse;
import app.openlosa.application.dto.CompanyResponse;
import app.openlosa.application.dto.StatusTransitionResponse;
import app.openlosa.application.dto.TagResponse;

final class ApplicationMapper {

    private ApplicationMapper() {
    }

    static CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
            company.getId(),
            company.getName(),
            company.getWebsite(),
            company.getNotes(),
            company.getCreatedAt()
        );
    }

    static ApplicationResponse toResponse(JobApplication application) {
        return new ApplicationResponse(
            application.getId(),
            toResponse(application.getCompany()),
            application.getRoleTitle(),
            application.getPostingUrl(),
            application.getLocation(),
            application.getStatus(),
            application.getAppliedAt(),
            application.getSource(),
            application.getSalaryText(),
            application.getNotes(),
            application.isFavorite(),
            application.getTags().stream()
                .map(ApplicationMapper::toResponse)
                .toList(),
            application.getCreatedAt(),
            application.getUpdatedAt()
        );
    }

    static TagResponse toResponse(Tag tag) {
        return new TagResponse(
            tag.getId(),
            tag.getName(),
            tag.getColor()
        );
    }

    static StatusTransitionResponse toResponse(StatusTransition transition) {
        return new StatusTransitionResponse(
            transition.getId(),
            transition.getApplication().getId(),
            transition.getFromStatus(),
            transition.getToStatus(),
            transition.getOccurredAt()
        );
    }
}
