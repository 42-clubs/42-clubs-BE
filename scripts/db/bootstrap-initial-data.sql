-- psql variables and execution procedure: docs/initial-data-setup.md
-- Run only with operator-verified 42 numeric IDs. Never commit filled-in copies.
BEGIN;

CREATE TEMP TABLE bootstrap_validation (
    valid BOOLEAN NOT NULL CHECK (valid)
) ON COMMIT DROP;
INSERT INTO bootstrap_validation VALUES (
    :'super_intra_id'::BIGINT > 0 AND :'manager_intra_id'::BIGINT > 0
    AND btrim(:'super_login') <> '' AND btrim(:'super_name') <> ''
    AND btrim(:'manager_login') <> '' AND btrim(:'manager_name') <> ''
    AND btrim(:'club_name') <> '' AND btrim(:'club_summary') <> ''
    AND btrim(:'club_description') <> ''
);

-- clubs.name is not UNIQUE: serialize bootstrap runs and reject ambiguous matches.
LOCK TABLE clubs IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE bootstrap_club ON COMMIT DROP AS
SELECT id FROM clubs WHERE name = :'club_name';
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM bootstrap_club) > 1 THEN
        RAISE EXCEPTION 'Multiple clubs have this name; resolve the target before bootstrap';
    END IF;
END $$;

WITH inserted AS (
    INSERT INTO clubs (name, summary, description, operation_status,
                       recruitment_planned, recruitment_closed_early, created_at, updated_at)
    SELECT :'club_name', :'club_summary', :'club_description', 'ACTIVE',
           false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM bootstrap_club)
    RETURNING id
)
INSERT INTO bootstrap_club SELECT id FROM inserted;

INSERT INTO users (intra_id, intra_login, display_name, role, created_at, updated_at)
VALUES (:'super_intra_id'::BIGINT, :'super_login', :'super_name', 'SUPER_ADMIN',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (intra_id) DO UPDATE
SET intra_login = EXCLUDED.intra_login, display_name = EXCLUDED.display_name,
    role = 'SUPER_ADMIN', updated_at = CURRENT_TIMESTAMP;

-- A chair starts as USER; an existing ADMIN/SUPER_ADMIN role is preserved.
INSERT INTO users (intra_id, intra_login, display_name, role, created_at, updated_at)
VALUES (:'manager_intra_id'::BIGINT, :'manager_login', :'manager_name', 'USER',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (intra_id) DO UPDATE
SET intra_login = EXCLUDED.intra_login, display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO club_managers (user_id, club_id, created_at)
SELECT users.id, bootstrap_club.id, CURRENT_TIMESTAMP
FROM users CROSS JOIN bootstrap_club
WHERE intra_id = :'manager_intra_id'::BIGINT
ON CONFLICT (user_id, club_id) DO NOTHING;

SELECT id, name, operation_status FROM clubs WHERE id IN (SELECT id FROM bootstrap_club);
SELECT id, intra_id, intra_login, role FROM users
WHERE intra_id IN (:'super_intra_id'::BIGINT, :'manager_intra_id'::BIGINT);
SELECT user_id, club_id FROM club_managers WHERE club_id IN (SELECT id FROM bootstrap_club);
COMMIT;
