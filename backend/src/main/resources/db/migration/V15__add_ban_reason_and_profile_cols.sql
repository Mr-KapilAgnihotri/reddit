-- ─────────────────────────────────────────────────────────────────────────────
-- V15 : User profile bio/avatar fields + ban_reason column on users
-- ─────────────────────────────────────────────────────────────────────────────
-- The user_profiles table already exists from V1.
-- We add a ban_reason column to users so admins can record why a user was banned.

ALTER TABLE users ADD COLUMN IF NOT EXISTS ban_reason TEXT;

-- Ensure user_profiles has the columns we reference in UserProfile entity
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS display_name VARCHAR(50);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS bio TEXT;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS preferences JSONB DEFAULT '{}';
