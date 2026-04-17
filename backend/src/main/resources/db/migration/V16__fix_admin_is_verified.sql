-- ─────────────────────────────────────────────────────────────────────────────
-- V16 : Fix admin user so they can log in (set is_verified = true)
-- ─────────────────────────────────────────────────────────────────────────────
-- The AdminDataInitializer was not setting is_verified=true when bootstrapping
-- the admin user.  Any existing admin row in the DB will be unable to log in
-- because AuthService.login() rejects users where is_verified is false.
--
-- This migration sets is_verified=true for every user that holds the ADMIN role.

UPDATE users
SET    is_verified = true
WHERE  id IN (
    SELECT ur.user_id
    FROM   user_roles ur
    JOIN   roles r ON r.id = ur.role_id
    WHERE  r.name = 'ADMIN'
);
