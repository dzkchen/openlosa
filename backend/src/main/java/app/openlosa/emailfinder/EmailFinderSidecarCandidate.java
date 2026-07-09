package app.openlosa.emailfinder;

record EmailFinderSidecarCandidate(
    String email,
    String founder,
    String status,
    Integer confidence,
    Integer rank,
    Integer permutationRank,
    String mxHost,
    Integer smtpCode,
    Integer latencyMs,
    Boolean catchAllDomain
) {
}
