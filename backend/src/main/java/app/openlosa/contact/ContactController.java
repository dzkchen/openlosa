package app.openlosa.contact;

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

import app.openlosa.contact.dto.ContactCreateRequest;
import app.openlosa.contact.dto.ContactResponse;
import app.openlosa.contact.dto.ContactUpdateRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/contacts")
class ContactController {

    private final ContactService contactService;

    ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    List<ContactResponse> list(
        @RequestParam(required = false) ContactRelationship relationship,
        @RequestParam(required = false) Long companyId,
        @RequestParam(required = false) String company,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contactedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contactedTo,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir
    ) {
        return contactService.list(relationship, companyId, company, q, contactedFrom, contactedTo, sort, dir);
    }

    @GetMapping("/{id}")
    ContactResponse get(@PathVariable Long id) {
        return contactService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContactResponse create(@Valid @RequestBody ContactCreateRequest request) {
        return contactService.create(request);
    }

    @PutMapping("/{id}")
    ContactResponse update(@PathVariable Long id, @Valid @RequestBody ContactUpdateRequest request) {
        return contactService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        contactService.delete(id);
    }
}
