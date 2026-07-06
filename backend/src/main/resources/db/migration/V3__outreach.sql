CREATE TABLE outreach (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NULL,
    company_id BIGINT NULL,
    application_id BIGINT NULL,
    type VARCHAR(40) NOT NULL DEFAULT 'COLD_EMAIL',
    status VARCHAR(40) NOT NULL DEFAULT 'TO_SEND',
    sent_at DATE NULL,
    follow_up_by DATE NULL,
    notes TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_outreach_contact FOREIGN KEY (contact_id) REFERENCES contact (id) ON DELETE SET NULL,
    CONSTRAINT fk_outreach_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE SET NULL,
    CONSTRAINT fk_outreach_application FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE SET NULL,
    CONSTRAINT chk_outreach_type CHECK (
        type IN (
            'COLD_EMAIL',
            'LINKEDIN_DM',
            'REFERRAL_ASK',
            'OTHER'
        )
    ),
    CONSTRAINT chk_outreach_status CHECK (
        status IN (
            'TO_SEND',
            'SENT',
            'REPLIED',
            'GHOSTED'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_outreach_contact_id ON outreach (contact_id);
CREATE INDEX ix_outreach_company_id ON outreach (company_id);
CREATE INDEX ix_outreach_application_id ON outreach (application_id);
CREATE INDEX ix_outreach_status ON outreach (status);
CREATE INDEX ix_outreach_type ON outreach (type);
CREATE INDEX ix_outreach_follow_up_by ON outreach (follow_up_by);
CREATE INDEX ix_outreach_sent_at ON outreach (sent_at);
