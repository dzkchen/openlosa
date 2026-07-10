package app.openlosa.prospect;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.ApplicationService;
import app.openlosa.application.Tag;
import app.openlosa.application.TagRepository;
import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.NotFoundException;
import app.openlosa.prospect.dto.ProspectCreateRequest;
import app.openlosa.prospect.dto.ProspectPromoteRequest;
import app.openlosa.prospect.dto.ProspectResponse;
import app.openlosa.prospect.dto.ProspectUpdateRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

@Service
public class ProspectService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "name", "name",
        "priority", "priority",
        "status", "status",
        "createdAt", "createdAt",
        "updatedAt", "updatedAt"
    );

    private final ProspectRepository prospectRepository;
    private final TagRepository tagRepository;
    private final ApplicationService applicationService;

    public ProspectService(
        ProspectRepository prospectRepository,
        TagRepository tagRepository,
        ApplicationService applicationService
    ) {
        this.prospectRepository = prospectRepository;
        this.tagRepository = tagRepository;
        this.applicationService = applicationService;
    }

    @Transactional(readOnly = true)
    public List<ProspectResponse> list(
        ProspectPriority priority,
        ProspectStatus status,
        Long tagId,
        String q,
        String sort,
        String dir,
        Integer page,
        Integer size
    ) {
        var specification = prospectSpec(priority, status, tagId, q);
        var ordering = sort(sort, dir);
        var prospects = page == null && size == null
            ? prospectRepository.findAll(specification, ordering)
            : prospectRepository.findAll(
                specification,
                PageRequest.of(page == null ? 0 : page, size == null ? 100 : size, ordering)
            ).getContent();
        return prospects.stream()
            .map(ProspectMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProspectResponse get(Long id) {
        return ProspectMapper.toResponse(requireProspect(id));
    }

    @Transactional
    public ProspectResponse create(ProspectCreateRequest request) {
        if (request.status() == ProspectStatus.PROMOTED) {
            throw new BadRequestException("Use promote endpoint to mark a prospect promoted");
        }
        var prospect = new Prospect(
            cleanRequired(request.name(), "name"),
            request.priority() == null ? ProspectPriority.MEDIUM : request.priority(),
            request.status() == null ? ProspectStatus.NEW : request.status()
        );
        prospect.setUrl(clean(request.url()));
        prospect.setNote(clean(request.note()));
        replaceTags(prospect, request.tagIds());
        return ProspectMapper.toResponse(prospectRepository.save(prospect));
    }

    @Transactional
    public Prospect createFromFeed(String name, String url, String note) {
        var prospect = new Prospect(cleanRequired(name, "name"), ProspectPriority.MEDIUM, ProspectStatus.NEW);
        prospect.setUrl(clean(url));
        prospect.setNote(clean(note));
        return prospectRepository.save(prospect);
    }

    @Transactional
    public ProspectResponse update(Long id, ProspectUpdateRequest request) {
        var prospect = requireProspectForUpdate(id);
        if (request.name() != null) {
            prospect.setName(cleanRequired(request.name(), "name"));
        }
        if (Boolean.TRUE.equals(request.clearUrl()) && request.url() != null) {
            throw new BadRequestException("url cannot be set and cleared in the same request");
        }
        if (Boolean.TRUE.equals(request.clearUrl())) {
            prospect.setUrl(null);
        } else if (request.url() != null) {
            prospect.setUrl(clean(request.url()));
        }
        if (Boolean.TRUE.equals(request.clearNote()) && request.note() != null) {
            throw new BadRequestException("note cannot be set and cleared in the same request");
        }
        if (Boolean.TRUE.equals(request.clearNote())) {
            prospect.setNote(null);
        } else if (request.note() != null) {
            prospect.setNote(clean(request.note()));
        }
        if (request.priority() != null) {
            prospect.setPriority(request.priority());
        }
        if (request.status() != null) {
            validateStatusUpdate(prospect, request.status());
            prospect.setStatus(request.status());
        }
        if (request.tagIds() != null) {
            replaceTags(prospect, request.tagIds());
        }
        return ProspectMapper.toResponse(prospect);
    }

    @Transactional
    public ProspectResponse promote(Long id, ProspectPromoteRequest request) {
        var prospect = requireProspectForUpdate(id);
        if (prospect.getPromotedApplication() == null) {
            var application = applicationService.createFromProspect(
                cleanOrDefault(request == null ? null : request.companyName(), prospect.getName()),
                cleanOrDefault(request == null ? null : request.roleTitle(), "Opportunity"),
                prospect.getUrl(),
                prospect.getNote(),
                prospect.getTags()
            );
            prospect.setPromotedApplication(application);
        }
        prospect.setStatus(ProspectStatus.PROMOTED);
        return ProspectMapper.toResponse(prospect);
    }

    @Transactional
    public void delete(Long id) {
        prospectRepository.delete(requireProspect(id));
    }

    private void replaceTags(Prospect prospect, List<Long> tagIds) {
        prospect.getTags().clear();
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        var uniqueTagIds = new LinkedHashSet<>(tagIds);
        for (Long tagId : uniqueTagIds) {
            if (tagId == null) {
                throw new BadRequestException("tagIds cannot contain null");
            }
            prospect.getTags().add(requireTag(tagId));
        }
    }

    private Prospect requireProspect(Long id) {
        return prospectRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Prospect " + id + " was not found"));
    }

    private Prospect requireProspectForUpdate(Long id) {
        return prospectRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Prospect " + id + " was not found"));
    }

    private Tag requireTag(Long id) {
        return tagRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Tag " + id + " was not found"));
    }

    private Specification<Prospect> prospectSpec(ProspectPriority priority, ProspectStatus status, Long tagId, String q) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            Join<Prospect, Tag> tagJoin = null;

            if (tagId != null) {
                tagJoin = root.join("tags", JoinType.LEFT);
                predicates.add(cb.equal(tagJoin.get("id"), tagId));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(q)) {
                if (tagJoin == null) {
                    tagJoin = root.join("tags", JoinType.LEFT);
                }
                var applicationJoin = root.join("promotedApplication", JoinType.LEFT);
                var companyJoin = applicationJoin.join("company", JoinType.LEFT);
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("url")), like),
                    cb.like(cb.lower(root.get("note")), like),
                    cb.like(cb.lower(tagJoin.get("name")), like),
                    cb.like(cb.lower(applicationJoin.get("roleTitle")), like),
                    cb.like(cb.lower(companyJoin.get("name")), like)
                ));
            }

            if (tagId != null || StringUtils.hasText(q)) {
                query.distinct(true);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort sort(String sort, String dir) {
        var property = SORT_FIELDS.getOrDefault(StringUtils.hasText(sort) ? sort : "createdAt", "createdAt");
        var direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by(direction, "id"));
    }

    private String cleanRequired(String value, String fieldName) {
        var cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BadRequestException(fieldName + " is required");
        }
        return cleaned;
    }

    private String cleanOrDefault(String value, String defaultValue) {
        var cleaned = clean(value);
        return cleaned == null ? defaultValue : cleaned;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateStatusUpdate(Prospect prospect, ProspectStatus status) {
        if (status == ProspectStatus.PROMOTED && prospect.getPromotedApplication() == null) {
            throw new BadRequestException("Use promote endpoint to mark a prospect promoted");
        }
        if (status != ProspectStatus.PROMOTED && prospect.getPromotedApplication() != null) {
            throw new BadRequestException("Promoted prospects must stay PROMOTED while linked to an application");
        }
    }
}
