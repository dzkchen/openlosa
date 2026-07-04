package app.openlosa.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.dto.ApplicationCreateRequest;
import app.openlosa.application.dto.ApplicationResponse;
import app.openlosa.application.dto.ApplicationUpdateRequest;
import app.openlosa.application.dto.StatusTransitionResponse;
import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.NotFoundException;
import jakarta.persistence.criteria.Predicate;

@Service
public class ApplicationService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "company", "company.name",
        "roleTitle", "roleTitle",
        "status", "status",
        "appliedAt", "appliedAt",
        "source", "source",
        "favorite", "favorite",
        "createdAt", "createdAt",
        "updatedAt", "updatedAt"
    );

    private final JobApplicationRepository applicationRepository;
    private final StatusTransitionRepository transitionRepository;
    private final TagRepository tagRepository;
    private final CompanyService companyService;
    private final Clock clock;

    public ApplicationService(
        JobApplicationRepository applicationRepository,
        StatusTransitionRepository transitionRepository,
        TagRepository tagRepository,
        CompanyService companyService
    ) {
        this.applicationRepository = applicationRepository;
        this.transitionRepository = transitionRepository;
        this.tagRepository = tagRepository;
        this.companyService = companyService;
        this.clock = Clock.systemDefaultZone();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(
        ApplicationStatus status,
        Long companyId,
        String company,
        Boolean favorite,
        ApplicationSource source,
        String q,
        LocalDate appliedFrom,
        LocalDate appliedTo,
        String sort,
        String dir
    ) {
        return applicationRepository.findAll(
                applicationSpec(status, companyId, company, favorite, source, q, appliedFrom, appliedTo),
                sort(sort, dir)
            ).stream()
            .map(ApplicationMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id) {
        return ApplicationMapper.toResponse(requireApplication(id));
    }

    @Transactional(readOnly = true)
    public List<StatusTransitionResponse> transitions(Long id) {
        requireApplication(id);
        return transitionRepository.findByApplicationIdOrderByOccurredAtAscIdAsc(id).stream()
            .map(ApplicationMapper::toResponse)
            .toList();
    }

    @Transactional
    public ApplicationResponse create(ApplicationCreateRequest request) {
        var company = companyService.resolveCompany(
            request.companyId(),
            request.companyName(),
            request.companyWebsite(),
            request.companyNotes()
        );
        var status = request.status() == null ? ApplicationStatus.SAVED : request.status();
        var source = request.source() == null ? ApplicationSource.MANUAL : request.source();
        var application = new JobApplication(company, cleanRequired(request.roleTitle(), "roleTitle"), status, source);

        applyEditableFields(application, request.postingUrl(), request.location(), request.appliedAt(),
            request.salaryText(), request.notes(), request.favorite());
        autoFillAppliedAt(application);

        var saved = applicationRepository.save(application);
        transitionRepository.save(new StatusTransition(saved, null, saved.getStatus()));

        return ApplicationMapper.toResponse(saved);
    }

    @Transactional
    public ApplicationResponse update(Long id, ApplicationUpdateRequest request) {
        var application = requireApplication(id);
        if (request.companyId() != null || StringUtils.hasText(request.companyName())) {
            application.setCompany(companyService.resolveCompany(
                request.companyId(),
                request.companyName(),
                request.companyWebsite(),
                request.companyNotes()
            ));
        }
        if (StringUtils.hasText(request.roleTitle())) {
            application.setRoleTitle(cleanRequired(request.roleTitle(), "roleTitle"));
        }
        if (request.source() != null) {
            application.setSource(request.source());
        }

        applyEditableFields(application, request.postingUrl(), request.location(), request.appliedAt(),
            request.salaryText(), request.notes(), request.favorite());

        if (request.status() != null && request.status() != application.getStatus()) {
            moveStatus(application, request.status());
        }

        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse changeStatus(Long id, ApplicationStatus toStatus) {
        var application = requireApplication(id);
        moveStatus(application, toStatus);
        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse undoStatus(Long id) {
        var application = requireApplication(id);
        var latestTransition = transitionRepository.findFirstByApplicationIdOrderByOccurredAtDescIdDesc(id)
            .orElseThrow(() -> new BadRequestException("Application has no status transition to undo"));

        if (latestTransition.getFromStatus() == null) {
            throw new BadRequestException("Initial status transition cannot be undone");
        }

        application.setStatus(latestTransition.getFromStatus());
        clearAutoFilledAppliedAtAfterUndo(application, latestTransition);
        transitionRepository.delete(latestTransition);
        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse setFavorite(Long id, Boolean favorite) {
        var application = requireApplication(id);
        application.setFavorite(favorite);
        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse attachTag(Long id, Long tagId) {
        var application = requireApplication(id);
        application.getTags().add(requireTag(tagId));
        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse detachTag(Long id, Long tagId) {
        var application = requireApplication(id);
        application.getTags().remove(requireTag(tagId));
        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public void delete(Long id) {
        applicationRepository.delete(requireApplication(id));
    }

    private void moveStatus(JobApplication application, ApplicationStatus toStatus) {
        if (toStatus == application.getStatus()) {
            return;
        }

        var fromStatus = application.getStatus();
        application.setStatus(toStatus);
        autoFillAppliedAt(application);
        transitionRepository.save(new StatusTransition(application, fromStatus, toStatus));
    }

    private void autoFillAppliedAt(JobApplication application) {
        if (application.getStatus() == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
            application.setAppliedAt(LocalDate.now(clock));
        }
    }

    private void clearAutoFilledAppliedAtAfterUndo(JobApplication application, StatusTransition transition) {
        if (transition.getToStatus() == ApplicationStatus.APPLIED
            && application.getAppliedAt() != null
            && application.getAppliedAt().equals(transition.getOccurredAt().toLocalDate())) {
            application.setAppliedAt(null);
        }
    }

    private JobApplication requireApplication(Long id) {
        return applicationRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Application " + id + " was not found"));
    }

    private Tag requireTag(Long id) {
        return tagRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Tag " + id + " was not found"));
    }

    private void applyEditableFields(
        JobApplication application,
        String postingUrl,
        String location,
        LocalDate appliedAt,
        String salaryText,
        String notes,
        Boolean favorite
    ) {
        if (postingUrl != null) {
            application.setPostingUrl(clean(postingUrl));
        }
        if (location != null) {
            application.setLocation(clean(location));
        }
        if (appliedAt != null) {
            application.setAppliedAt(appliedAt);
        }
        if (salaryText != null) {
            application.setSalaryText(clean(salaryText));
        }
        if (notes != null) {
            application.setNotes(clean(notes));
        }
        if (favorite != null) {
            application.setFavorite(favorite);
        }
    }

    private Specification<JobApplication> applicationSpec(
        ApplicationStatus status,
        Long companyId,
        String company,
        Boolean favorite,
        ApplicationSource source,
        String q,
        LocalDate appliedFrom,
        LocalDate appliedTo
    ) {
        return (root, query, cb) -> {
            query.distinct(true);
            var predicates = new java.util.ArrayList<Predicate>();
            var companyJoin = root.join("company");

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (companyId != null) {
                predicates.add(cb.equal(companyJoin.get("id"), companyId));
            }
            if (StringUtils.hasText(company)) {
                predicates.add(cb.like(cb.lower(companyJoin.get("name")), "%" + company.trim().toLowerCase() + "%"));
            }
            if (favorite != null) {
                predicates.add(cb.equal(root.get("favorite"), favorite));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (appliedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appliedAt"), appliedFrom));
            }
            if (appliedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("appliedAt"), appliedTo));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("roleTitle")), like),
                    cb.like(cb.lower(root.get("postingUrl")), like),
                    cb.like(cb.lower(root.get("location")), like),
                    cb.like(cb.lower(root.get("notes")), like),
                    cb.like(cb.lower(companyJoin.get("name")), like)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort sort(String sort, String dir) {
        var property = SORT_FIELDS.getOrDefault(StringUtils.hasText(sort) ? sort : "createdAt", "createdAt");
        var direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private String cleanRequired(String value, String fieldName) {
        var cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BadRequestException(fieldName + " is required");
        }
        return cleaned;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
