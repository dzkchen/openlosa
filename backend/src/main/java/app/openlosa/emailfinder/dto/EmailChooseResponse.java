package app.openlosa.emailfinder.dto;

public record EmailChooseResponse(
    Long lookupId,
    String chosenEmail,
    Long contactId,
    Long outreachId
) {
}
