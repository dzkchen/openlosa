package app.openlosa.feed;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.openlosa.common.api.PagedResponse;
import app.openlosa.feed.dto.FeedHealthResponse;
import app.openlosa.feed.dto.FeedJobHideRequest;
import app.openlosa.feed.dto.FeedJobResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping("/api/v1/feed")
class FeedController {

    private final FeedService feedService;

    FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/health")
    FeedHealthResponse health() {
        return feedService.health();
    }

    @GetMapping("/jobs")
    PagedResponse<FeedJobResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String sponsorship,
        @RequestParam(required = false) Boolean open,
        @RequestParam(required = false) Boolean hidden,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate postedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate postedTo,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir,
        @RequestParam(required = false) @Min(0) Integer page,
        @RequestParam(required = false) @Min(1) Integer size
    ) {
        return feedService.list(q, sponsorship, open, hidden, postedFrom, postedTo, sort, dir, page, size);
    }

    @PostMapping("/jobs/{id}/hide")
    FeedJobResponse hide(@PathVariable Long id, @Valid @RequestBody FeedJobHideRequest request) {
        return feedService.setHidden(id, request.hidden());
    }

    @PostMapping("/jobs/{id}/save-prospect")
    FeedJobResponse saveProspect(@PathVariable Long id) {
        return feedService.saveProspect(id);
    }

    @PostMapping("/jobs/{id}/create-application")
    FeedJobResponse createApplication(@PathVariable Long id) {
        return feedService.createApplication(id);
    }
}
