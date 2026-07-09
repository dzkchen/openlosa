package app.openlosa.outreach;

import static app.openlosa.outreach.OutreachStatus.GHOSTED;
import static app.openlosa.outreach.OutreachStatus.REPLIED;
import static app.openlosa.outreach.OutreachStatus.SENT;
import static app.openlosa.outreach.OutreachStatus.TO_SEND;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.CompanyService;
import app.openlosa.application.JobApplication;
import app.openlosa.application.JobApplicationRepository;
import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.NotFoundException;
import app.openlosa.contact.Contact;
import app.openlosa.contact.ContactRepository;
import app.openlosa.outreach.dto.OutreachCreateRequest;
import app.openlosa.outreach.dto.OutreachResponse;
import app.openlosa.outreach.dto.OutreachUpdateRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
public class OutreachService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "contact", "contact.name",
        "company", "company.name",
        "application", "application.roleTitle",
        "type", "type",
        "status", "status",
        "sentAt", "sentAt",
        "followUpBy", "followUpBy",
        "createdAt", "createdAt",
        "updatedAt", "updatedAt"
    );

    private final OutreachRepository outreachRepository;
    private final ContactRepository contactRepository;
    private final CompanyService companyService;
    private final JobApplicationRepository applicationRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public OutreachService(
        OutreachRepository outreachRepository,
        ContactRepository contactRepository,
        CompanyService companyService,
        JobApplicationRepository applicationRepository
    ) {
        this.outreachRepository = outreachRepository;
        this.contactRepository = contactRepository;
        this.companyService = companyService;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public List<OutreachResponse> list(
        OutreachStatus status,
        OutreachType type,
        Long contactId,
        Long companyId,
        String company,
        String q,
        LocalDate sentFrom,
        LocalDate sentTo,
        LocalDate followUpFrom,
        LocalDate followUpTo,
        String sort,
        String dir
    ) {
        return outreachRepository.findAll(
                outreachSpec(status, type, contactId, companyId, company, q, sentFrom, sentTo, followUpFrom, followUpTo),
                sort(sort, dir)
            ).stream()
            .map(OutreachMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public OutreachResponse get(Long id) {
        return OutreachMapper.toResponse(requireOutreach(id));
    }

    @Transactional(readOnly = true)
    public List<OutreachResponse> due() {
        var today = LocalDate.now(clock);
        return outreachRepository.findAll(dueSpec(today), Sort.by(
                Sort.Order.asc("status"),
                Sort.Order.asc("followUpBy"),
                Sort.Order.asc("createdAt")
            )).stream()
            .map(OutreachMapper::toResponse)
            .toList();
    }

    @Transactional
    public OutreachResponse create(OutreachCreateRequest request) {
        var outreach = new Outreach(
            request.type() == null ? OutreachType.COLD_EMAIL : request.type(),
            TO_SEND
        );
        applyAssociations(
            outreach,
            request.contactId(),
            false,
            request.companyId(),
            request.companyName(),
            request.companyWebsite(),
            request.companyNotes(),
            false,
            request.applicationId(),
            false
        );
        outreach.setFollowUpBy(request.followUpBy());
        outreach.setNotes(clean(request.notes()));

        var targetStatus = request.status() == null ? TO_SEND : request.status();
        if (targetStatus == TO_SEND && request.sentAt() != null) {
            throw new BadRequestException("sentAt requires status SENT");
        }
        if (targetStatus != TO_SEND) {
            moveStatus(outreach, targetStatus, request.sentAt());
        }

        return OutreachMapper.toResponse(outreachRepository.save(outreach));
    }

    @Transactional
    public OutreachResponse update(Long id, OutreachUpdateRequest request) {
        var outreach = requireOutreach(id);
        var contactChanged = applyAssociations(
            outreach,
            request.contactId(),
            Boolean.TRUE.equals(request.clearContact()),
            request.companyId(),
            request.companyName(),
            request.companyWebsite(),
            request.companyNotes(),
            Boolean.TRUE.equals(request.clearCompany()),
            request.applicationId(),
            Boolean.TRUE.equals(request.clearApplication())
        );

        if (request.type() != null) {
            outreach.setType(request.type());
        }
        if (request.notes() != null) {
            outreach.setNotes(clean(request.notes()));
        }
        if (Boolean.TRUE.equals(request.clearFollowUpBy()) && request.followUpBy() != null) {
            throw new BadRequestException("followUpBy cannot be set and cleared in the same request");
        }
        if (Boolean.TRUE.equals(request.clearFollowUpBy())) {
            outreach.setFollowUpBy(null);
        } else if (request.followUpBy() != null) {
            outreach.setFollowUpBy(request.followUpBy());
        }

        var clearsSentAt = Boolean.TRUE.equals(request.clearSentAt());
        if (clearsSentAt && request.sentAt() != null) {
            throw new BadRequestException("sentAt cannot be set and cleared in the same request");
        }
        var targetStatus = request.status() == null ? outreach.getStatus() : request.status();
        if (clearsSentAt && targetStatus != TO_SEND) {
            throw new BadRequestException("sentAt is required once outreach has been sent");
        }
        if (request.status() != null) {
            moveStatus(outreach, request.status(), request.sentAt());
            if (clearsSentAt) {
                outreach.setSentAt(null);
            }
        } else if (clearsSentAt) {
            outreach.setSentAt(null);
        } else if (request.sentAt() != null) {
            if (outreach.getStatus() == TO_SEND) {
                throw new BadRequestException("sentAt requires status SENT");
            }
            outreach.setSentAt(request.sentAt());
            touchContactLastContacted(outreach);
        }
        if (contactChanged) {
            touchContactLastContacted(outreach);
        }

        return OutreachMapper.toResponse(outreach);
    }

    @Transactional
    public void delete(Long id) {
        outreachRepository.delete(requireOutreach(id));
    }

    private boolean applyAssociations(
        Outreach outreach,
        Long contactId,
        boolean clearContact,
        Long companyId,
        String companyName,
        String companyWebsite,
        String companyNotes,
        boolean clearCompany,
        Long applicationId,
        boolean clearApplication
    ) {
        if (clearContact && contactId != null) {
            throw new BadRequestException("contact cannot be set and cleared in the same request");
        }
        if (clearCompany && (companyId != null || StringUtils.hasText(companyName))) {
            throw new BadRequestException("company cannot be set and cleared in the same request");
        }
        if (clearApplication && applicationId != null) {
            throw new BadRequestException("application cannot be set and cleared in the same request");
        }

        var previousContactId = outreach.getContact() == null ? null : outreach.getContact().getId();

        if (clearContact) {
            outreach.setContact(null);
        } else if (contactId != null) {
            var contact = requireContact(contactId);
            outreach.setContact(contact);
            if (outreach.getCompany() == null && contact.getCompany() != null) {
                outreach.setCompany(contact.getCompany());
            }
        }

        if (clearCompany) {
            outreach.setCompany(null);
        } else if (companyId != null || StringUtils.hasText(companyName)) {
            outreach.setCompany(companyService.resolveCompany(companyId, companyName, companyWebsite, companyNotes));
        }

        if (clearApplication) {
            outreach.setApplication(null);
        } else if (applicationId != null) {
            var application = requireApplication(applicationId);
            outreach.setApplication(application);
            if (outreach.getCompany() == null) {
                outreach.setCompany(application.getCompany());
            }
        }

        var nextContactId = outreach.getContact() == null ? null : outreach.getContact().getId();
        return !Objects.equals(previousContactId, nextContactId);
    }

    private void moveStatus(Outreach outreach, OutreachStatus toStatus, LocalDate requestedSentAt) {
        var fromStatus = outreach.getStatus();
        if (toStatus == fromStatus) {
            if (toStatus == SENT && requestedSentAt != null) {
                outreach.setSentAt(requestedSentAt);
                touchContactLastContacted(outreach);
            }
            return;
        }

        if (!isAllowedTransition(fromStatus, toStatus)) {
            throw new BadRequestException("Invalid outreach status transition " + fromStatus + " -> " + toStatus);
        }

        if (toStatus == SENT) {
            outreach.setSentAt(requestedSentAt == null ? LocalDate.now(clock) : requestedSentAt);
        }
        outreach.setStatus(toStatus);
        touchContactLastContacted(outreach);
    }

    private boolean isAllowedTransition(OutreachStatus fromStatus, OutreachStatus toStatus) {
        return (fromStatus == TO_SEND && toStatus == SENT)
            || (fromStatus == SENT && (toStatus == REPLIED || toStatus == GHOSTED));
    }

    private void touchContactLastContacted(Outreach outreach) {
        if (outreach.getStatus() == TO_SEND || outreach.getContact() == null || outreach.getSentAt() == null) {
            return;
        }

        var lastContactedAt = outreach.getContact().getLastContactedAt();
        if (lastContactedAt == null || lastContactedAt.isBefore(outreach.getSentAt())) {
            outreach.getContact().setLastContactedAt(outreach.getSentAt());
        }
    }

    private Outreach requireOutreach(Long id) {
        return outreachRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Outreach " + id + " was not found"));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Contact " + id + " was not found"));
    }

    private JobApplication requireApplication(Long id) {
        return applicationRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Application " + id + " was not found"));
    }

    private Specification<Outreach> outreachSpec(
        OutreachStatus status,
        OutreachType type,
        Long contactId,
        Long companyId,
        String company,
        String q,
        LocalDate sentFrom,
        LocalDate sentTo,
        LocalDate followUpFrom,
        LocalDate followUpTo
    ) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            var contactJoin = root.join("contact", JoinType.LEFT);
            var companyJoin = root.join("company", JoinType.LEFT);
            var applicationJoin = root.join("application", JoinType.LEFT);

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (contactId != null) {
                predicates.add(cb.equal(contactJoin.get("id"), contactId));
            }
            if (companyId != null) {
                predicates.add(cb.equal(companyJoin.get("id"), companyId));
            }
            if (StringUtils.hasText(company)) {
                predicates.add(cb.like(cb.lower(companyJoin.get("name")), "%" + company.trim().toLowerCase() + "%"));
            }
            if (sentFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), sentFrom));
            }
            if (sentTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), sentTo));
            }
            if (followUpFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("followUpBy"), followUpFrom));
            }
            if (followUpTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("followUpBy"), followUpTo));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("notes")), like),
                    cb.like(cb.lower(contactJoin.get("name")), like),
                    cb.like(cb.lower(contactJoin.get("email")), like),
                    cb.like(cb.lower(companyJoin.get("name")), like),
                    cb.like(cb.lower(applicationJoin.get("roleTitle")), like)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Outreach> dueSpec(LocalDate today) {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("status"), TO_SEND),
            cb.and(
                cb.equal(root.get("status"), SENT),
                cb.lessThanOrEqualTo(root.get("followUpBy"), today)
            )
        );
    }

    private Sort sort(String sort, String dir) {
        var property = SORT_FIELDS.getOrDefault(StringUtils.hasText(sort) ? sort : "createdAt", "createdAt");
        var direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
