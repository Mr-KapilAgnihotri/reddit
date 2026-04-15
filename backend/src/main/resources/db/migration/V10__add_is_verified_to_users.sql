ALTER TABLE users ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing accounts remain able to sign in after this migration
UPDATE users SET is_verified = true;
