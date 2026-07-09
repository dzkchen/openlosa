package app.openlosa.emailfinder.dto;

import java.time.LocalDateTime;
import java.util.List;

import app.openlosa.emailfinder.EmailLookupStatus;

public record EmailLookupResponse(
    Long id,
    Long contactId,
    String personName,
    String companyUrl,
    String domain,
    String company,
    Integer requestedCount,
    Integer permutationsProbed,
    EmailLookupStatus topStatus,
    Integer topConfidence,
    String chosenEmail,
    List<EmailCandidateResponse> candidates,
    LocalDateTime createdAt
) {
}
