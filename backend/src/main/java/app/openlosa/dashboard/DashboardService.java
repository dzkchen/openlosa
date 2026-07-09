package app.openlosa.dashboard;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.openlosa.dashboard.dto.DashboardStatsResponse;

@Service
class DashboardService {

    private final DashboardStatsRepository statsRepository;
    private final int noUpdateThresholdDays;
    private final Clock clock;

    @Autowired
    DashboardService(
        DashboardStatsRepository statsRepository,
        @Value("${openlosa.dashboard.no-update-threshold-days}") int noUpdateThresholdDays
    ) {
        this(statsRepository, noUpdateThresholdDays, Clock.systemDefaultZone());
    }

    DashboardService(
        DashboardStatsRepository statsRepository,
        int noUpdateThresholdDays,
        Clock clock
    ) {
        if (noUpdateThresholdDays < 1) {
            throw new IllegalArgumentException("No-update threshold must be at least one day");
        }
        this.statsRepository = statsRepository;
        this.noUpdateThresholdDays = noUpdateThresholdDays;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    DashboardStatsResponse getStats() {
        LocalDateTime now = LocalDateTime.now(clock);
        return statsRepository.getStats(now.toLocalDate(), now.minusDays(noUpdateThresholdDays));
    }
}
