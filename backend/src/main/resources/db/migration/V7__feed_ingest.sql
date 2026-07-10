CREATE TABLE feed_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    engine_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    location VARCHAR(1000) NULL,
    source_ats VARCHAR(80) NOT NULL,
    sponsorship VARCHAR(40) NULL,
    posted_at DATE NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    is_open BOOLEAN NOT NULL DEFAULT TRUE,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    missed_successful_ingests INT NOT NULL DEFAULT 0,
    saved_prospect_id BIGINT NULL,
    created_application_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_feed_job_engine_id UNIQUE (engine_id),
    CONSTRAINT fk_feed_job_saved_prospect
        FOREIGN KEY (saved_prospect_id) REFERENCES prospect (id) ON DELETE SET NULL,
    CONSTRAINT fk_feed_job_created_application
        FOREIGN KEY (created_application_id) REFERENCES application (id) ON DELETE SET NULL,
    CONSTRAINT chk_feed_job_missed_ingests CHECK (missed_successful_ingests <= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_feed_job_open_hidden_posted
    ON feed_job (is_open, hidden, posted_at);
CREATE INDEX ix_feed_job_source_ats
    ON feed_job (source_ats);
CREATE INDEX ix_feed_job_last_seen_at
    ON feed_job (last_seen_at);
CREATE INDEX ix_feed_job_saved_prospect_id
    ON feed_job (saved_prospect_id);
CREATE INDEX ix_feed_job_created_application_id
    ON feed_job (created_application_id);

CREATE TABLE feed_ingest_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ran_at DATETIME(6) NOT NULL,
    jobs_seen INT NOT NULL DEFAULT 0,
    jobs_new INT NOT NULL DEFAULT 0,
    jobs_closed INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    message TEXT NULL,
    file_fingerprint VARCHAR(64) NULL,
    file_modified_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_feed_ingest_run_status CHECK (
        status IN ('SUCCESS', 'SKIPPED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_feed_ingest_run_status_ran_at
    ON feed_ingest_run (status, ran_at);
CREATE INDEX ix_feed_ingest_run_ran_at
    ON feed_ingest_run (ran_at);
