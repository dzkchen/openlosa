package app.openlosa.contact;

import app.openlosa.application.Company;
import app.openlosa.application.dto.CompanyResponse;
import app.openlosa.contact.dto.ContactResponse;

final class ContactMapper {

    private ContactMapper() {
    }

    static ContactResponse toResponse(Contact contact) {
        return new ContactResponse(
            contact.getId(),
            toCompanyResponse(contact.getCompany()),
            contact.getName(),
            contact.getTitle(),
            contact.getEmail(),
            contact.getLinkedinUrl(),
            contact.getRelationship(),
            contact.getNotes(),
            contact.getLastContactedAt(),
            contact.getCreatedAt()
        );
    }

    private static CompanyResponse toCompanyResponse(Company company) {
        if (company == null) {
            return null;
        }

        return new CompanyResponse(
            company.getId(),
            company.getName(),
            company.getWebsite(),
            company.getNotes(),
            company.getCreatedAt()
        );
    }
}
