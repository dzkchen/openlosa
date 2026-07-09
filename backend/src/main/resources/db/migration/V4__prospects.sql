CREATE TABLE prospect (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NULL,
    note TEXT NULL,
    priority VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(40) NOT NULL DEFAULT 'NEW',
    promoted_application_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_prospect_promoted_application FOREIGN KEY (promoted_application_id) REFERENCES application (id) ON DELETE SET NULL,
    CONSTRAINT chk_prospect_priority CHECK (
        priority IN (
            'LOW',
            'MEDIUM',
            'HIGH'
        )
    ),
    CONSTRAINT chk_prospect_status CHECK (
        status IN (
            'NEW',
            'RESEARCHING',
            'PROMOTED',
            'DROPPED'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prospect_tag (
    prospect_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (prospect_id, tag_id),
    CONSTRAINT fk_prospect_tag_prospect FOREIGN KEY (prospect_id) REFERENCES prospect (id) ON DELETE CASCADE,
    CONSTRAINT fk_prospect_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_prospect_priority ON prospect (priority);
CREATE INDEX ix_prospect_status ON prospect (status);
CREATE INDEX ix_prospect_created_at ON prospect (created_at);
CREATE INDEX ix_prospect_promoted_application_id ON prospect (promoted_application_id);
CREATE INDEX ix_prospect_tag_tag_id ON prospect_tag (tag_id);
