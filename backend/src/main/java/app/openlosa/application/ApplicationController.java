package app.openlosa.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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

import app.openlosa.application.dto.ApplicationCreateRequest;
import app.openlosa.application.dto.ApplicationResponse;
import app.openlosa.application.dto.ApplicationStatusRequest;
import app.openlosa.application.dto.ApplicationUpdateRequest;
import app.openlosa.application.dto.StatusTransitionResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/applications")
class ApplicationController {

    private final ApplicationService applicationService;

    ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    List<ApplicationResponse> list(
        @RequestParam(required = false) ApplicationStatus status,
        @RequestParam(required = false) Long companyId,
        @RequestParam(required = false) String company,
        @RequestParam(required = false) Boolean favorite,
        @RequestParam(required = false) ApplicationSource source,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedTo,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir
    ) {
        return applicationService.list(status, companyId, company, favorite, source, q, appliedFrom, appliedTo, sort, dir);
    }

    @GetMapping("/{id}")
    ApplicationResponse get(@PathVariable Long id) {
        return applicationService.get(id);
    }

    @GetMapping("/{id}/status-transitions")
    List<StatusTransitionResponse> transitions(@PathVariable Long id) {
        return applicationService.transitions(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationResponse create(@Valid @RequestBody ApplicationCreateRequest request) {
        return applicationService.create(request);
    }

    @PutMapping("/{id}")
    ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody ApplicationUpdateRequest request) {
        return applicationService.update(id, request);
    }

    @PostMapping("/{id}/status")
    ApplicationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ApplicationStatusRequest request) {
        return applicationService.changeStatus(id, request.toStatus());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        applicationService.delete(id);
    }
}
