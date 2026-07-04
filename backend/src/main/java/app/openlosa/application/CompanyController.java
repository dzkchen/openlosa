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

import app.openlosa.application.dto.CompanyRequest;
import app.openlosa.application.dto.CompanyResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/companies")
class CompanyController {

    private final CompanyService companyService;

    CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    List<CompanyResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir
    ) {
        return companyService.list(q, sort, dir);
    }

    @GetMapping("/{id}")
    CompanyResponse get(@PathVariable Long id) {
        return companyService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CompanyResponse create(@Valid @RequestBody CompanyRequest request) {
        return companyService.create(request);
    }

    @PutMapping("/{id}")
    CompanyResponse update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return companyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        companyService.delete(id);
    }
}
