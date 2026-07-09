package app.openlosa.dashboard;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.openlosa.dashboard.dto.DashboardSankeyLinkResponse;
import app.openlosa.dashboard.dto.DashboardStatsResponse;

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    DashboardStatsResponse stats() {
        return dashboardService.getStats();
    }

    @GetMapping("/sankey")
    List<DashboardSankeyLinkResponse> sankey() {
        return dashboardService.getSankeyLinks();
    }
}
