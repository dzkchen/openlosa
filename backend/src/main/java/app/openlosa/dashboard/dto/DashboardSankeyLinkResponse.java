package app.openlosa.dashboard.dto;

public record DashboardSankeyLinkResponse(
    String from,
    String to,
    long count
) {
}
