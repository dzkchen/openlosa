CREATE INDEX ix_feed_job_hidden_posted_id
    ON feed_job (hidden, posted_at, id);
