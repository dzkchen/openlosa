package app.openlosa.application;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.dto.TagRequest;
import app.openlosa.application.dto.TagResponse;
import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.LikeQueries;
import app.openlosa.common.api.NotFoundException;
import jakarta.persistence.criteria.Predicate;

@Service
public class TagService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "name", "name",
        "color", "color"
    );

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list(String q, String sort, String dir) {
        return tagRepository.findAll(tagSpec(q), sort(sort, dir)).stream()
            .map(ApplicationMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TagResponse get(Long id) {
        return ApplicationMapper.toResponse(requireTag(id));
    }

    @Transactional
    public TagResponse create(TagRequest request) {
        var tag = new Tag(cleanRequired(request.name(), "name"), clean(request.color()));
        return ApplicationMapper.toResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse update(Long id, TagRequest request) {
        var tag = requireTag(id);
        tag.setName(cleanRequired(request.name(), "name"));
        tag.setColor(clean(request.color()));
        return ApplicationMapper.toResponse(tag);
    }

    @Transactional
    public void delete(Long id) {
        tagRepository.delete(requireTag(id));
    }

    private Tag requireTag(Long id) {
        return tagRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Tag " + id + " was not found"));
    }

    private Specification<Tag> tagSpec(String q) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(q)) {
                return cb.conjunction();
            }

            String like = LikeQueries.contains(q);
            Predicate name = cb.like(cb.lower(root.get("name")), like, LikeQueries.ESCAPE);
            Predicate color = cb.like(cb.lower(root.get("color")), like, LikeQueries.ESCAPE);
            return cb.or(name, color);
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
