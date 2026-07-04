CREATE TABLE company (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    website VARCHAR(2048) NULL,
    notes TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_company_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    role_title VARCHAR(255) NOT NULL,
    posting_url VARCHAR(2048) NULL,
    location VARCHAR(255) NULL,
    status VARCHAR(40) NOT NULL,
    applied_at DATE NULL,
    source VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    salary_text VARCHAR(255) NULL,
    notes TEXT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_application_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT chk_application_status CHECK (
        status IN (
            'SAVED',
            'APPLIED',
            'ONLINE_ASSESSMENT',
            'PHONE_SCREEN',
            'INTERVIEW',
            'OFFER',
            'REJECTED',
            'WITHDRAWN',
            'GHOSTED'
        )
    ),
    CONSTRAINT chk_application_source CHECK (source IN ('MANUAL', 'FEED', 'PROSPECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_application_company_id ON application (company_id);
CREATE INDEX ix_application_status ON application (status);
CREATE INDEX ix_application_favorite ON application (favorite);

CREATE TABLE status_transition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    from_status VARCHAR(40) NULL,
    to_status VARCHAR(40) NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_status_transition_application FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE CASCADE,
    CONSTRAINT chk_status_transition_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'SAVED',
            'APPLIED',
            'ONLINE_ASSESSMENT',
            'PHONE_SCREEN',
            'INTERVIEW',
            'OFFER',
            'REJECTED',
            'WITHDRAWN',
            'GHOSTED'
        )
    ),
    CONSTRAINT chk_status_transition_to_status CHECK (
        to_status IN (
            'SAVED',
            'APPLIED',
            'ONLINE_ASSESSMENT',
            'PHONE_SCREEN',
            'INTERVIEW',
            'OFFER',
            'REJECTED',
            'WITHDRAWN',
            'GHOSTED'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_status_transition_application_id ON status_transition (application_id);
CREATE INDEX ix_status_transition_occurred_at ON status_transition (occurred_at);

CREATE TABLE tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    color VARCHAR(40) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tag_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_tag (
    application_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (application_id, tag_id),
    CONSTRAINT fk_application_tag_application FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_application_tag_tag_id ON application_tag (tag_id);
