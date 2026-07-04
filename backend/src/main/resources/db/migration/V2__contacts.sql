CREATE TABLE contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    title VARCHAR(255) NULL,
    email VARCHAR(255) NULL,
    linkedin_url VARCHAR(2048) NULL,
    relationship VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    notes TEXT NULL,
    last_contacted_at DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_contact_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE SET NULL,
    CONSTRAINT chk_contact_relationship CHECK (
        relationship IN (
            'RECRUITER',
            'ALUM',
            'REFERRAL',
            'OTHER'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_contact_company_id ON contact (company_id);
CREATE INDEX ix_contact_relationship ON contact (relationship);
CREATE INDEX ix_contact_last_contacted_at ON contact (last_contacted_at);
