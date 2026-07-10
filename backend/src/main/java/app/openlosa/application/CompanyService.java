package app.openlosa.application;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.dto.CompanyRequest;
import app.openlosa.application.dto.CompanyResponse;
import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.LikeQueries;
import app.openlosa.common.api.NotFoundException;
import jakarta.persistence.criteria.Predicate;

@Service
public class CompanyService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "name", "name",
        "createdAt", "createdAt"
    );

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list(String q, String sort, String dir) {
        return companyRepository.findAll(companySpec(q), sort(sort, dir)).stream()
            .map(ApplicationMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(Long id) {
        return ApplicationMapper.toResponse(requireCompany(id));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        var company = new Company(cleanRequired(request.name(), "name"), clean(request.website()), clean(request.notes()));
        return ApplicationMapper.toResponse(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyRequest request) {
        var company = requireCompany(id);
        company.setName(cleanRequired(request.name(), "name"));
        company.setWebsite(clean(request.website()));
        company.setNotes(clean(request.notes()));
        return ApplicationMapper.toResponse(company);
    }

    @Transactional
    public void delete(Long id) {
        companyRepository.delete(requireCompany(id));
    }

    @Transactional
    public Company resolveCompany(Long companyId, String companyName, String companyWebsite, String companyNotes) {
        if (companyId != null) {
            return requireCompany(companyId);
        }

        var cleanedName = cleanRequired(companyName, "companyName");
        return companyRepository.findByNameIgnoreCase(cleanedName)
            .map(company -> mergeMissingDetails(company, companyWebsite, companyNotes))
            .orElseGet(() -> companyRepository.save(new Company(cleanedName, clean(companyWebsite), clean(companyNotes))));
    }

    private Company mergeMissingDetails(Company company, String website, String notes) {
        if (!StringUtils.hasText(company.getWebsite()) && StringUtils.hasText(website)) {
            company.setWebsite(clean(website));
        }
        if (!StringUtils.hasText(company.getNotes()) && StringUtils.hasText(notes)) {
            company.setNotes(clean(notes));
        }
        return company;
    }

    private Company requireCompany(Long id) {
        return companyRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Company " + id + " was not found"));
    }

    private Specification<Company> companySpec(String q) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(q)) {
                return cb.conjunction();
            }

            String like = LikeQueries.contains(q);
            Predicate name = cb.like(cb.lower(root.get("name")), like, LikeQueries.ESCAPE);
            Predicate website = cb.like(cb.lower(root.get("website")), like, LikeQueries.ESCAPE);
            return cb.or(name, website);
        };
    }

    private Sort sort(String sort, String dir) {
        var property = SORT_FIELDS.getOrDefault(StringUtils.hasText(sort) ? sort : "name", "name");
        var direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
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
