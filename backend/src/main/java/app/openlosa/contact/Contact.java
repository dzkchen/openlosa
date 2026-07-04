package app.openlosa.contact;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import app.openlosa.application.Company;
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
@Table(name = "contact")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false)
    private String name;

    private String title;

    private String email;

    @Column(name = "linkedin_url", length = 2048)
    private String linkedinUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContactRelationship relationship;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_contacted_at")
    private LocalDate lastContactedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Contact() {
    }

    public Contact(String name, ContactRelationship relationship) {
        this.name = name;
        this.relationship = relationship;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public ContactRelationship getRelationship() {
        return relationship;
    }

    public void setRelationship(ContactRelationship relationship) {
        this.relationship = relationship;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getLastContactedAt() {
        return lastContactedAt;
    }

    public void setLastContactedAt(LocalDate lastContactedAt) {
        this.lastContactedAt = lastContactedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
