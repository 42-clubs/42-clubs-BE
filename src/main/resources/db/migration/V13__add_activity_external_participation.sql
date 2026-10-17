-- Existing activity data is absent by agreement; do not guess a policy for legacy rows.
ALTER TABLE activities ADD COLUMN allow_external_participants BOOLEAN NOT NULL;
