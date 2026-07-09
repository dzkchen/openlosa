package app.openlosa.emailfinder;

record EmailFinderSidecarRequest(
    String personName,
    String companyUrl,
    Integer count,
    Boolean includeCatchAll,
    Boolean includeUnknown,
    Boolean noSmtp,
    Double delaySeconds
) {
}
