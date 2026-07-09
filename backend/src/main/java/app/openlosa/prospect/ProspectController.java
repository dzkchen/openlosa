package app.openlosa.prospect;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import app.openlosa.prospect.dto.ProspectCreateRequest;
import app.openlosa.prospect.dto.ProspectPromoteRequest;
import app.openlosa.prospect.dto.ProspectResponse;
import app.openlosa.prospect.dto.ProspectUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping("/api/v1/prospects")
class ProspectController {

    private final ProspectService prospectService;

    ProspectController(ProspectService prospectService) {
        this.prospectService = prospectService;
    }

    @GetMapping
    List<ProspectResponse> list(
        @RequestParam(required = false) ProspectPriority priority,
        @RequestParam(required = false) ProspectStatus status,
        @RequestParam(required = false) Long tagId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir,
        @RequestParam(required = false) @Min(0) Integer page,
        @RequestParam(required = false) @Min(1) @Max(100) Integer size
    ) {
        return prospectService.list(priority, status, tagId, q, sort, dir, page, size);
    }

    @GetMapping("/{id}")
    ProspectResponse get(@PathVariable Long id) {
        return prospectService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProspectResponse create(@Valid @RequestBody ProspectCreateRequest request) {
        return prospectService.create(request);
    }

    @PatchMapping("/{id}")
    ProspectResponse update(@PathVariable Long id, @Valid @RequestBody ProspectUpdateRequest request) {
        return prospectService.update(id, request);
    }

    /**
     * Retained temporarily for clients created before the partial-update API was aligned with the
     * documented PATCH contract.
     */
    @PutMapping("/{id}")
    ProspectResponse updateLegacy(@PathVariable Long id, @Valid @RequestBody ProspectUpdateRequest request) {
        return prospectService.update(id, request);
    }

    @PostMapping("/{id}/promote")
    ProspectResponse promote(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) ProspectPromoteRequest request
    ) {
        return prospectService.promote(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        prospectService.delete(id);
    }
}
