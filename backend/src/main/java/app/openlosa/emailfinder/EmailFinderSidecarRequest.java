package app.openlosa.emailfinder;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
