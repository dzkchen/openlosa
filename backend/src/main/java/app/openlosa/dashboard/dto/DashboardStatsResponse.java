package app.openlosa.dashboard.dto;

public record DashboardStatsResponse(
    long applicationsLast7Days,
    long applicationsLast30Days,
    long applicationsLast60Days,
    long noUpdate,
    long offers,
    long ongoing
) {
}
