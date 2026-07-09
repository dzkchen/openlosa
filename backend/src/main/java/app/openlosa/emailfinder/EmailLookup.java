package app.openlosa.emailfinder;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import app.openlosa.contact.Contact;
import app.openlosa.outreach.Outreach;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_lookup")
public class EmailLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(name = "person_name", nullable = false)
    private String personName;

    @Column(name = "company_url", nullable = false, length = 2048)
    private String companyUrl;

    @Column(name = "chosen_email")
    private String chosenEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chosen_outreach_id")
    private Outreach chosenOutreach;

    @Enumerated(EnumType.STRING)
    @Column(name = "top_status", length = 40)
    private EmailLookupStatus topStatus;

    @Column(name = "top_confidence")
    private Integer topConfidence;

    @Column(name = "candidates_json", nullable = false, columnDefinition = "json")
    private String candidatesJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected EmailLookup() {
    }

    public EmailLookup(String personName, String companyUrl, String candidatesJson) {
        this.personName = personName;
        this.companyUrl = companyUrl;
        this.candidatesJson = candidatesJson;
    }

    public Long getId() {
        return id;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public String getPersonName() {
        return personName;
    }

    public String getCompanyUrl() {
        return companyUrl;
    }

    public String getChosenEmail() {
        return chosenEmail;
    }

    public void setChosenEmail(String chosenEmail) {
        this.chosenEmail = chosenEmail;
    }

    public Outreach getChosenOutreach() {
        return chosenOutreach;
    }

    public void setChosenOutreach(Outreach chosenOutreach) {
        this.chosenOutreach = chosenOutreach;
    }

    public EmailLookupStatus getTopStatus() {
        return topStatus;
    }

    public void setTopStatus(EmailLookupStatus topStatus) {
        this.topStatus = topStatus;
    }

    public Integer getTopConfidence() {
        return topConfidence;
    }

    public void setTopConfidence(Integer topConfidence) {
        this.topConfidence = topConfidence;
    }

    public String getCandidatesJson() {
        return candidatesJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
