-- Retain closed recruitment when converting former dormant clubs to active clubs.
UPDATE clubs
SET operation_status = 'ACTIVE',
    recruitment_closed_early = recruitment_planned,
    updated_at = CURRENT_TIMESTAMP
WHERE operation_status = 'DORMANT';

ALTER TABLE clubs DROP CONSTRAINT ck_clubs_operation_status;
ALTER TABLE clubs ADD CONSTRAINT ck_clubs_operation_status
    CHECK (operation_status IN ('ACTIVE', 'ARCHIVED'));
