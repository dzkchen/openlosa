package app.openlosa.application;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import app.openlosa.application.dto.TagRequest;
import app.openlosa.application.dto.TagResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tags")
class TagController {

    private final TagService tagService;

    TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    List<TagResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir
    ) {
        return tagService.list(q, sort, dir);
    }

    @GetMapping("/{id}")
    TagResponse get(@PathVariable Long id) {
        return tagService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TagResponse create(@Valid @RequestBody TagRequest request) {
        return tagService.create(request);
    }

    @PutMapping("/{id}")
    TagResponse update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return tagService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        tagService.delete(id);
    }
}
