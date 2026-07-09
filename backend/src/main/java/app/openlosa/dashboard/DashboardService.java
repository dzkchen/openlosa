package app.openlosa.dashboard;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.openlosa.dashboard.dto.DashboardSankeyLinkResponse;
import app.openlosa.dashboard.dto.DashboardStatsResponse;

@Service
class DashboardService {

    private final DashboardStatsRepository statsRepository;
    private final DashboardSankeyRepository sankeyRepository;
    private final int noUpdateThresholdDays;
    private final Clock clock;

    @Autowired
    DashboardService(
        DashboardStatsRepository statsRepository,
        DashboardSankeyRepository sankeyRepository,
        @Value("${openlosa.dashboard.no-update-threshold-days}") int noUpdateThresholdDays
    ) {
        this(statsRepository, sankeyRepository, noUpdateThresholdDays, Clock.systemDefaultZone());
    }

    DashboardService(
        DashboardStatsRepository statsRepository,
        DashboardSankeyRepository sankeyRepository,
        int noUpdateThresholdDays,
        Clock clock
    ) {
        if (noUpdateThresholdDays < 1) {
            throw new IllegalArgumentException("No-update threshold must be at least one day");
        }
        this.statsRepository = statsRepository;
        this.sankeyRepository = sankeyRepository;
        this.noUpdateThresholdDays = noUpdateThresholdDays;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    DashboardStatsResponse getStats() {
        LocalDateTime now = LocalDateTime.now(clock);
        return statsRepository.getStats(now.toLocalDate(), now.minusDays(noUpdateThresholdDays));
    }

    @Transactional(readOnly = true)
    List<DashboardSankeyLinkResponse> getSankeyLinks() {
        LocalDateTime noUpdateCutoff = LocalDateTime.now(clock).minusDays(noUpdateThresholdDays);
        return sankeyRepository.getLinks(noUpdateCutoff).stream()
            .map(edge -> new DashboardSankeyLinkResponse(
                displayName(edge.from()),
                displayName(edge.to()),
                edge.count()
            ))
            .toList();
    }

    private String displayName(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
