package app.openlosa.outreach;

import app.openlosa.application.Company;
import app.openlosa.application.JobApplication;
import app.openlosa.application.dto.CompanyResponse;
import app.openlosa.contact.Contact;
import app.openlosa.contact.dto.ContactResponse;
import app.openlosa.outreach.dto.OutreachApplicationResponse;
import app.openlosa.outreach.dto.OutreachResponse;

final class OutreachMapper {

    private OutreachMapper() {
    }

    static OutreachResponse toResponse(Outreach outreach) {
        return new OutreachResponse(
            outreach.getId(),
            toContactResponse(outreach.getContact()),
            toCompanyResponse(outreach.getCompany()),
            toApplicationResponse(outreach.getApplication()),
            outreach.getType(),
            outreach.getStatus(),
            outreach.getSentAt(),
            outreach.getFollowUpBy(),
            outreach.getNotes(),
            outreach.getCreatedAt(),
            outreach.getUpdatedAt()
        );
    }

    private static ContactResponse toContactResponse(Contact contact) {
        if (contact == null) {
            return null;
        }

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

    private static OutreachApplicationResponse toApplicationResponse(JobApplication application) {
        if (application == null) {
            return null;
        }

        return new OutreachApplicationResponse(
            application.getId(),
            application.getRoleTitle(),
            application.getCompany().getName()
        );
    }
}
