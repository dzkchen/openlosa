-- A promoted prospect without its linked application violates the promotion invariant. Existing
-- rows produced before this migration are returned to the active research queue before the FK is
-- tightened so users can promote them again deliberately.
UPDATE prospect
SET status = 'RESEARCHING'
WHERE status = 'PROMOTED'
  AND promoted_application_id IS NULL;

ALTER TABLE prospect
    DROP FOREIGN KEY fk_prospect_promoted_application;

ALTER TABLE prospect
    ADD CONSTRAINT fk_prospect_promoted_application
    FOREIGN KEY (promoted_application_id) REFERENCES application (id) ON DELETE RESTRICT;
