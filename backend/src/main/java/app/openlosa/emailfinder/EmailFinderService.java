package app.openlosa.emailfinder;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.NotFoundException;
import app.openlosa.common.api.TooManyRequestsException;
import app.openlosa.contact.Contact;
import app.openlosa.contact.ContactRepository;
import app.openlosa.emailfinder.dto.EmailCandidateResponse;
import app.openlosa.emailfinder.dto.EmailChooseRequest;
import app.openlosa.emailfinder.dto.EmailChooseResponse;
import app.openlosa.emailfinder.dto.EmailLookupRequest;
import app.openlosa.emailfinder.dto.EmailLookupResponse;
import app.openlosa.outreach.Outreach;
import app.openlosa.outreach.OutreachRepository;
import app.openlosa.outreach.OutreachStatus;
import app.openlosa.outreach.OutreachType;

@Service
public class EmailFinderService {

    private static final TypeReference<List<EmailCandidateResponse>> CANDIDATE_LIST =
        new TypeReference<>() {
        };

    private final EmailLookupRepository emailLookupRepository;
    private final ContactRepository contactRepository;
    private final OutreachRepository outreachRepository;
    private final EmailFinderSidecarClient sidecarClient;
    private final ObjectMapper objectMapper;
    private final Semaphore lookupPermit = new Semaphore(1, true);

    public EmailFinderService(
        EmailLookupRepository emailLookupRepository,
        ContactRepository contactRepository,
        OutreachRepository outreachRepository,
        EmailFinderSidecarClient sidecarClient,
        ObjectMapper objectMapper
    ) {
        this.emailLookupRepository = emailLookupRepository;
        this.contactRepository = contactRepository;
        this.outreachRepository = outreachRepository;
        this.sidecarClient = sidecarClient;
        this.objectMapper = objectMapper;
    }

    public EmailLookupResponse lookup(EmailLookupRequest request) {
        var personName = cleanRequired(request.personName(), "personName");
        var companyUrl = cleanRequired(request.companyUrl(), "companyUrl");
        var contact = request.contactId() == null ? null : requireContact(request.contactId());

        var sidecarResponse = findWithPermit(new EmailFinderSidecarRequest(
                personName,
                companyUrl,
                request.count(),
                request.includeCatchAll(),
                request.includeUnknown(),
                request.noSmtp(),
                request.delaySeconds()
            ));
        var candidates = normalizeCandidates(sidecarResponse == null ? List.of() : sidecarResponse.candidates());
        var lookup = new EmailLookup(personName, companyUrl, writeCandidates(candidates));
        lookup.setContact(contact);
        candidates.stream().findFirst().ifPresent(candidate -> {
            lookup.setTopStatus(candidate.status());
            lookup.setTopConfidence(candidate.confidence());
        });

        var saved = emailLookupRepository.saveAndFlush(lookup);
        return toResponse(saved, sidecarResponse, candidates);
    }

    @Transactional(readOnly = true)
    public EmailLookupResponse getLookup(Long lookupId) {
        var lookup = requireLookup(lookupId);
        return toResponse(lookup, null, readCandidates(lookup));
    }

    @Transactional(readOnly = true)
    public List<EmailLookupResponse> listLookups(Long contactId) {
        requireContact(contactId);
        return emailLookupRepository.findTop5ByContactIdOrderByCreatedAtDesc(contactId).stream()
            .map(lookup -> toResponse(lookup, null, readCandidates(lookup)))
            .toList();
    }

    @Transactional
    public EmailChooseResponse choose(Long lookupId, EmailChooseRequest request) {
        var lookup = requireLookupForUpdate(lookupId);
        var chosenEmail = cleanRequired(request.email(), "email").toLowerCase(Locale.ROOT);
        var canonicalCandidate = readCandidates(lookup).stream()
            .filter(candidate -> candidate.email() != null && candidate.email().equalsIgnoreCase(chosenEmail))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("email must match one of the lookup candidates"));

        var contact = chooseContact(lookup, request.contactId());
        contact.setEmail(canonicalCandidate.email());
        lookup.setContact(contact);
        lookup.setChosenEmail(canonicalCandidate.email());

        var outreach = lookup.getChosenOutreach();
        if (!Boolean.FALSE.equals(request.createOutreach())) {
            if (outreach == null) {
                outreach = new Outreach(OutreachType.COLD_EMAIL, OutreachStatus.TO_SEND);
                outreach.setContact(contact);
                if (contact.getCompany() != null) {
                    outreach.setCompany(contact.getCompany());
                }
                lookup.setChosenOutreach(outreachRepository.save(outreach));
            }
        }

        return new EmailChooseResponse(
            lookup.getId(),
            lookup.getChosenEmail(),
            contact.getId(),
            lookup.getChosenOutreach() == null ? null : lookup.getChosenOutreach().getId()
        );
    }

    private Contact chooseContact(EmailLookup lookup, Long requestedContactId) {
        if (lookup.getContact() != null && requestedContactId != null && !lookup.getContact().getId().equals(requestedContactId)) {
            throw new BadRequestException("contactId does not match the lookup contact");
        }
        if (requestedContactId != null) {
            return requireContact(requestedContactId);
        }
        if (lookup.getContact() == null) {
            throw new BadRequestException("contactId is required to choose an email");
        }
        return lookup.getContact();
    }

    private EmailFinderSidecarResponse findWithPermit(EmailFinderSidecarRequest request) {
        if (!lookupPermit.tryAcquire()) {
            throw new TooManyRequestsException("An Email Finder lookup is already in progress; try again shortly");
        }
        try {
            return sidecarClient.find(request);
        } finally {
            lookupPermit.release();
        }
    }

    private EmailLookupResponse toResponse(
        EmailLookup lookup,
        EmailFinderSidecarResponse sidecarResponse,
        List<EmailCandidateResponse> candidates
    ) {
        return new EmailLookupResponse(
            lookup.getId(),
            lookup.getContact() == null ? null : lookup.getContact().getId(),
            lookup.getPersonName(),
            lookup.getCompanyUrl(),
            sidecarResponse == null ? null : sidecarResponse.domain(),
            sidecarResponse == null ? null : sidecarResponse.company(),
            sidecarResponse == null ? null : sidecarResponse.requestedCount(),
            sidecarResponse == null ? null : sidecarResponse.permutationsProbed(),
            lookup.getTopStatus(),
            lookup.getTopConfidence(),
            lookup.getChosenEmail(),
            candidates,
            lookup.getCreatedAt()
        );
    }

    private List<EmailCandidateResponse> normalizeCandidates(List<EmailFinderSidecarCandidate> candidates) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
            .filter(candidate -> StringUtils.hasText(candidate.email()))
            .map(candidate -> new EmailCandidateResponse(
                candidate.email().trim().toLowerCase(Locale.ROOT),
                clean(candidate.founder()),
                normalizeStatus(candidate.status()),
                candidate.confidence(),
                candidate.rank(),
                candidate.permutationRank(),
                clean(candidate.mxHost()),
                candidate.smtpCode(),
                candidate.latencyMs(),
                candidate.catchAllDomain()
            ))
            .toList();
    }

    private EmailLookupStatus normalizeStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus)) {
            return EmailLookupStatus.UNKNOWN;
        }
        var normalized = rawStatus.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
        try {
            return EmailLookupStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return EmailLookupStatus.UNKNOWN;
        }
    }

    private String writeCandidates(List<EmailCandidateResponse> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize email lookup candidates", exception);
        }
    }

    private List<EmailCandidateResponse> readCandidates(EmailLookup lookup) {
        try {
            return objectMapper.readValue(lookup.getCandidatesJson(), CANDIDATE_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read email lookup candidates", exception);
        }
    }

    private EmailLookup requireLookup(Long id) {
        return emailLookupRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Email lookup " + id + " was not found"));
    }

    private EmailLookup requireLookupForUpdate(Long id) {
        return emailLookupRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Email lookup " + id + " was not found"));
    }

    private Contact requireContact(Long id) {
        return contactRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Contact " + id + " was not found"));
    }

    private String cleanRequired(String value, String fieldName) {
        var cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BadRequestException(fieldName + " is required");
        }
        return cleaned;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
