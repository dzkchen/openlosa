CREATE TABLE email_lookup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NULL,
    person_name VARCHAR(255) NOT NULL,
    company_url VARCHAR(2048) NOT NULL,
    chosen_email VARCHAR(255) NULL,
    chosen_outreach_id BIGINT NULL,
    top_status VARCHAR(40) NULL,
    top_confidence INT NULL,
    candidates_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_email_lookup_contact FOREIGN KEY (contact_id) REFERENCES contact (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_lookup_chosen_outreach FOREIGN KEY (chosen_outreach_id) REFERENCES outreach (id) ON DELETE SET NULL,
    CONSTRAINT chk_email_lookup_top_status CHECK (
        top_status IN (
            'VERIFIED',
            'CATCH_ALL',
            'UNKNOWN',
            'DOES_NOT_EXIST'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_email_lookup_contact_id ON email_lookup (contact_id);
CREATE INDEX ix_email_lookup_chosen_outreach_id ON email_lookup (chosen_outreach_id);
CREATE INDEX ix_email_lookup_created_at ON email_lookup (created_at);
