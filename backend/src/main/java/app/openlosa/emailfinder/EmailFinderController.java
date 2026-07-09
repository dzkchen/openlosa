package app.openlosa.emailfinder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import app.openlosa.common.api.BadRequestException;
import app.openlosa.emailfinder.dto.EmailChooseRequest;
import app.openlosa.emailfinder.dto.EmailChooseResponse;
import app.openlosa.emailfinder.dto.EmailLookupRequest;
import app.openlosa.emailfinder.dto.EmailLookupResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/email-finder")
class EmailFinderController {

    private final EmailFinderService emailFinderService;

    EmailFinderController(EmailFinderService emailFinderService) {
        this.emailFinderService = emailFinderService;
    }

    @PostMapping("/lookup")
    @ResponseStatus(HttpStatus.CREATED)
    EmailLookupResponse lookup(@Valid @RequestBody EmailLookupRequest request) {
        return emailFinderService.lookup(request);
    }

    @PostMapping("/{lookupId}/choose")
    EmailChooseResponse choose(@PathVariable Long lookupId, @Valid @RequestBody EmailChooseRequest request) {
        if (request.lookupId() != null && !request.lookupId().equals(lookupId)) {
            throw new BadRequestException("lookupId in the request body must match the path");
        }
        return emailFinderService.choose(lookupId, request);
    }

    @PostMapping("/choose")
    EmailChooseResponse choose(@Valid @RequestBody EmailChooseRequest request) {
        if (request.lookupId() == null) {
            throw new BadRequestException("lookupId is required");
        }
        return emailFinderService.choose(request.lookupId(), request);
    }
}
