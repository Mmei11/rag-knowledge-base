ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role;

ALTER TABLE users
    ADD CONSTRAINT ck_users_role
    CHECK (role IN ('USER', 'ADMIN', 'SUPER_ADMIN'));

UPDATE users
SET role = 'SUPER_ADMIN',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT MIN(id) FROM users WHERE role = 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM users WHERE role = 'SUPER_ADMIN');
