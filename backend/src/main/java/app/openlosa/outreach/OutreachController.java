package app.openlosa.outreach;

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

import app.openlosa.outreach.dto.OutreachCreateRequest;
import app.openlosa.outreach.dto.OutreachResponse;
import app.openlosa.outreach.dto.OutreachUpdateRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/outreach")
class OutreachController {

    private final OutreachService outreachService;

    OutreachController(OutreachService outreachService) {
        this.outreachService = outreachService;
    }

    @GetMapping
    List<OutreachResponse> list(
        @RequestParam(required = false) OutreachStatus status,
        @RequestParam(required = false) OutreachType type,
        @RequestParam(required = false) Long contactId,
        @RequestParam(required = false) Long companyId,
        @RequestParam(required = false) String company,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sentFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sentTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpTo,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir
    ) {
        return outreachService.list(status, type, contactId, companyId, company, q, sentFrom, sentTo, followUpFrom, followUpTo, sort, dir);
    }

    @GetMapping("/due")
    List<OutreachResponse> due() {
        return outreachService.due();
    }

    @GetMapping("/{id}")
    OutreachResponse get(@PathVariable Long id) {
        return outreachService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OutreachResponse create(@Valid @RequestBody OutreachCreateRequest request) {
        return outreachService.create(request);
    }

    @PutMapping("/{id}")
    OutreachResponse update(@PathVariable Long id, @Valid @RequestBody OutreachUpdateRequest request) {
        return outreachService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        outreachService.delete(id);
    }
}
