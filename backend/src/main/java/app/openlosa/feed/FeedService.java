package app.openlosa.feed;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import app.openlosa.application.ApplicationService;
import app.openlosa.common.api.LikeQueries;
import app.openlosa.common.api.NotFoundException;
import app.openlosa.common.api.PagedResponse;
import app.openlosa.feed.dto.FeedHealthResponse;
import app.openlosa.feed.dto.FeedJobResponse;
import app.openlosa.prospect.ProspectService;
import jakarta.persistence.criteria.Predicate;

@Service
public class FeedService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "company", "companyName",
        "title", "title",
        "sponsorship", "sponsorship",
        "postedAt", "postedAt",
        "lastSeenAt", "lastSeenAt"
    );
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final FeedJobRepository feedJobRepository;
    private final FeedIngestRunRepository runRepository;
    private final ProspectService prospectService;
    private final ApplicationService applicationService;
    private final int staleAfterHours;
    private final Clock clock;

    @Autowired
    FeedService(
        FeedJobRepository feedJobRepository,
        FeedIngestRunRepository runRepository,
        ProspectService prospectService,
        ApplicationService applicationService,
        @Value("${openlosa.feed.stale-after-hours}") int staleAfterHours
    ) {
        this(
            feedJobRepository,
            runRepository,
            prospectService,
            applicationService,
            staleAfterHours,
            Clock.systemUTC()
        );
    }

    FeedService(
        FeedJobRepository feedJobRepository,
        FeedIngestRunRepository runRepository,
        ProspectService prospectService,
        ApplicationService applicationService,
        int staleAfterHours,
        Clock clock
    ) {
        if (staleAfterHours < 1) {
            throw new IllegalArgumentException("Feed stale threshold must be at least one hour");
        }
        this.feedJobRepository = feedJobRepository;
        this.runRepository = runRepository;
        this.prospectService = prospectService;
        this.applicationService = applicationService;
        this.staleAfterHours = staleAfterHours;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FeedHealthResponse health() {
        var lastRun = runRepository.findFirstByOrderByIdDesc().orElse(null);
        var lastSuccess = runRepository.findFirstByStatusOrderByIdDesc(FeedIngestStatus.SUCCESS).orElse(null);
        var lastFresh = runRepository
            .findFirstByFileFingerprintIsNotNullAndStatusInOrderByIdDesc(FeedIngestStatus.FINGERPRINTED)
            .orElse(null);
        long openJobs = feedJobRepository.countByOpenTrueAndHiddenFalse();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(staleAfterHours);
        boolean stale = lastFresh == null || lastFresh.getRanAt().isBefore(cutoff);
        return new FeedHealthResponse(
            lastRun == null ? null : FeedMapper.toRunSummary(lastRun),
            lastSuccess == null ? null : lastSuccess.getRanAt(),
            openJobs,
            stale,
            staleAfterHours
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<FeedJobResponse> list(
        String q,
        String sponsorship,
        Boolean open,
        Boolean hidden,
        LocalDate postedFrom,
        LocalDate postedTo,
        String sort,
        String dir,
        Integer page,
        Integer size
    ) {
        var pageable = pageable(page, size, sort, dir);
        var jobs = feedJobRepository.findAll(feedSpec(q, sponsorship, open, hidden, postedFrom, postedTo), pageable)
            .map(FeedMapper::toResponse);
        return PagedResponse.of(jobs);
    }

    @Transactional
    public FeedJobResponse setHidden(Long id, boolean hidden) {
        var job = feedJobRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Feed job " + id + " was not found"));
        job.setHidden(hidden);
        return FeedMapper.toResponse(job);
    }

    @Transactional
    public FeedJobResponse saveProspect(Long id) {
        var job = requireJobForUpdate(id);
        if (job.getSavedProspect() == null) {
            var prospect = prospectService.createFromFeed(
                job.getCompanyName() + " — " + job.getTitle(),
                job.getUrl(),
                buildProspectNote(job)
            );
            job.setSavedProspect(prospect);
        }
        return FeedMapper.toResponse(job);
    }

    @Transactional
    public FeedJobResponse createApplication(Long id) {
        var job = requireJobForUpdate(id);
        if (job.getCreatedApplication() == null) {
            var application = applicationService.createFromFeed(
                job.getCompanyName(),
                job.getTitle(),
                job.getUrl(),
                job.getLocation()
            );
            job.setCreatedApplication(application);
        }
        return FeedMapper.toResponse(job);
    }

    private FeedJob requireJobForUpdate(Long id) {
        return feedJobRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Feed job " + id + " was not found"));
    }

    private String buildProspectNote(FeedJob job) {
        var note = new StringBuilder("Saved from feed via ").append(job.getSourceAts()).append('.');
        if (StringUtils.hasText(job.getLocation())) {
            note.append(" Location: ").append(job.getLocation()).append('.');
        }
        return note.toString();
    }

    private Specification<FeedJob> feedSpec(
        String q,
        String sponsorship,
        Boolean open,
        Boolean hidden,
        LocalDate postedFrom,
        LocalDate postedTo
    ) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (!Boolean.TRUE.equals(hidden)) {
                predicates.add(cb.isFalse(root.get("hidden")));
            }
            if (open != null) {
                predicates.add(cb.equal(root.get("open"), open));
            }
            if (StringUtils.hasText(sponsorship)) {
                predicates.add(cb.equal(root.get("sponsorship"), sponsorship));
            }
            if (postedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("postedAt"), postedFrom));
            }
            if (postedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("postedAt"), postedTo));
            }
            if (StringUtils.hasText(q)) {
                var like = LikeQueries.contains(q);
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("companyName")), like, LikeQueries.ESCAPE),
                    cb.like(cb.lower(root.get("title")), like, LikeQueries.ESCAPE)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Pageable pageable(Integer page, Integer size, String sort, String dir) {
        var pageNumber = page == null ? 0 : page;
        var pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize, sort(sort, dir));
    }

    private Sort sort(String sort, String dir) {
        var property = SORT_FIELDS.getOrDefault(StringUtils.hasText(sort) ? sort : "postedAt", "postedAt");
        var direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
