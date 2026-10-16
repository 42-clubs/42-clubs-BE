ALTER TABLE gathering_sessions ADD COLUMN title VARCHAR(200);

-- Existing sessions have no independent title; retain their gathering name as a fallback.
UPDATE gathering_sessions s SET title = g.title
FROM gatherings g WHERE s.gathering_id = g.id;

ALTER TABLE gathering_sessions ALTER COLUMN title SET NOT NULL;
