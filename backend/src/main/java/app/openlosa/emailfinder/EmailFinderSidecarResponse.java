package app.openlosa.emailfinder;

import java.util.List;

record EmailFinderSidecarResponse(
    String domain,
    String company,
    Integer requestedCount,
    Integer permutationsProbed,
    List<EmailFinderSidecarCandidate> candidates,
    String timestamp
) {
}
