package app.openlosa.emailfinder.dto;

import app.openlosa.emailfinder.EmailLookupStatus;

public record EmailCandidateResponse(
    String email,
    String founder,
    EmailLookupStatus status,
    Integer confidence,
    Integer rank,
    Integer permutationRank,
    String mxHost,
    Integer smtpCode,
    Integer latencyMs,
    Boolean catchAllDomain
) {
}
