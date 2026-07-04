package app.openlosa.contact;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.CompanyService;
import app.openlosa.contact.dto.ContactCreateRequest;
import app.openlosa.contact.dto.ContactResponse;
import app.openlosa.contact.dto.ContactUpdateRequest;
import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.NotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
public class ContactService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "name", "name",
        "company", "company.name",
        "title", "title",
        "email", "email",
        "relationship", "relationship",
        "lastContactedAt", "lastContactedAt",
        "createdAt", "createdAt"
    );

    private final ContactRepository contactRepository;
    private final CompanyService companyService;

    public ContactService(ContactRepository contactRepository, CompanyService companyService) {
        this.contactRepository = contactRepository;
        this.companyService = companyService;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> list(
        ContactRelationship relationship,
        Long companyId,
        String company,
        String q,
        LocalDate contactedFrom,
        LocalDate contactedTo,
        String sort,
        String dir
    ) {
        return contactRepository.findAll(
                contactSpec(relationship, companyId, company, q, contactedFrom, contactedTo),
                sort(sort, dir)
            ).stream()
            .map(ContactMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ContactResponse get(Long id) {
        return ContactMapper.toResponse(requireContact(id));
    }

    @Transactional
    public ContactResponse create(ContactCreateRequest request) {
        var contact = new Contact(
            cleanRequired(request.name(), "name"),
            request.relationship() == null ? ContactRelationship.OTHER : request.relationship()
        );
        if (request.companyId() != null || StringUtils.hasText(request.companyName())) {
            contact.setCompany(companyService.resolveCompany(
                request.companyId(),
                request.companyName(),
                request.companyWebsite(),
                request.companyNotes()
            ));
        }
        applyEditableFields(contact, request.title(), request.email(), request.linkedinUrl(), request.notes(),
            request.lastContactedAt(), false);
        return ContactMapper.toResponse(contactRepository.save(contact));
    }

    @Transactional
    public ContactResponse update(Long id, ContactUpdateRequest request) {
        var contact = requireContact(id);
        var clearCompany = Boolean.TRUE.equals(request.clearCompany());
        if (clearCompany && (request.companyId() != null || StringUtils.hasText(request.companyName()))) {
            throw new BadRequestException("company cannot be set and cleared in the same request");
        }
        if (clearCompany) {
            contact.setCompany(null);
        } else if (request.companyId() != null || StringUtils.hasText(request.companyName())) {
            contact.setCompany(companyService.resolveCompany(
                request.companyId(),
                request.companyName(),
                request.companyWebsite(),
                request.companyNotes()
            ));
        }
        if (request.name() != null) {
            contact.setName(cleanRequired(request.name(), "name"));
        }
        if (request.relationship() != null) {
            contact.setRelationship(request.relationship());
        }

        var clearLastContactedAt = Boolean.TRUE.equals(request.clearLastContactedAt());
        if (clearLastContactedAt && request.lastContactedAt() != null) {
            throw new BadRequestException("lastContactedAt cannot be set and cleared in the same request");
        }

        applyEditableFields(contact, request.title(), request.email(), request.linkedinUrl(), request.notes(),
            request.lastContactedAt(), clearLastContactedAt);
        return ContactMapper.toResponse(contact);
    }

    @Transactional
    public void delete(Long id) {
        contactRepository.delete(requireContact(id));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Contact " + id + " was not found"));
    }

    private void applyEditableFields(
        Contact contact,
        String title,
        String email,
        String linkedinUrl,
        String notes,
        LocalDate lastContactedAt,
        boolean clearLastContactedAt
    ) {
        if (title != null) {
            contact.setTitle(clean(title));
        }
        if (email != null) {
            contact.setEmail(clean(email));
        }
        if (linkedinUrl != null) {
            contact.setLinkedinUrl(clean(linkedinUrl));
        }
        if (notes != null) {
            contact.setNotes(clean(notes));
        }
        if (clearLastContactedAt) {
            contact.setLastContactedAt(null);
        } else if (lastContactedAt != null) {
            contact.setLastContactedAt(lastContactedAt);
        }
    }

    private Specification<Contact> contactSpec(
        ContactRelationship relationship,
        Long companyId,
        String company,
        String q,
        LocalDate contactedFrom,
        LocalDate contactedTo
    ) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            var companyJoin = root.join("company", JoinType.LEFT);

            if (relationship != null) {
                predicates.add(cb.equal(root.get("relationship"), relationship));
            }
            if (companyId != null) {
                predicates.add(cb.equal(companyJoin.get("id"), companyId));
            }
            if (StringUtils.hasText(company)) {
                predicates.add(cb.like(cb.lower(companyJoin.get("name")), "%" + company.trim().toLowerCase() + "%"));
            }
            if (contactedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastContactedAt"), contactedFrom));
            }
            if (contactedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("lastContactedAt"), contactedTo));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("linkedinUrl")), like),
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
