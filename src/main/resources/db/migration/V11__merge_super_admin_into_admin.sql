-- Preserve user identities and all relations while removing the separate highest role.
UPDATE users SET role = 'ADMIN', updated_at = CURRENT_TIMESTAMP WHERE role = 'SUPER_ADMIN';
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
