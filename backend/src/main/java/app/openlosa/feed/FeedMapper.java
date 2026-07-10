package app.openlosa.feed;

import app.openlosa.feed.dto.FeedIngestRunSummary;
import app.openlosa.feed.dto.FeedJobResponse;

final class FeedMapper {

    private FeedMapper() {
    }

    static FeedIngestRunSummary toRunSummary(FeedIngestRun run) {
        return new FeedIngestRunSummary(
            run.getRanAt(),
            run.getStatus().name(),
            run.getMessage(),
            run.getJobsSeen(),
            run.getJobsNew(),
            run.getJobsClosed()
        );
    }

    static FeedJobResponse toResponse(FeedJob job) {
        // getId() on a lazy @ManyToOne proxy reads the stored FK without triggering a
        // full load, so mapping the promotion links stays a single query on the list page.
        var savedProspect = job.getSavedProspect();
        var createdApplication = job.getCreatedApplication();
        return new FeedJobResponse(
            job.getId(),
            job.getCompanyName(),
            job.getTitle(),
            job.getUrl(),
            job.getLocation(),
            job.getSourceAts(),
            job.getSponsorship(),
            job.getPostedAt(),
            job.getFirstSeenAt(),
            job.getLastSeenAt(),
            job.isOpen(),
            job.isHidden(),
            savedProspect == null ? null : savedProspect.getId(),
            createdApplication == null ? null : createdApplication.getId()
        );
    }
}
