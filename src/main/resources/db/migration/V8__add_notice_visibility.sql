ALTER TABLE notices ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'MEMBERS_ONLY';
ALTER TABLE notices ADD CONSTRAINT ck_notices_visibility CHECK (visibility IN ('MEMBERS_ONLY', 'PUBLIC'));
CREATE INDEX idx_notices_club_visibility_created ON notices(club_id, visibility, created_at DESC, id DESC);
