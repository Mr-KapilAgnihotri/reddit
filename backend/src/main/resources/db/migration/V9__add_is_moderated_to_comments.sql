-- Add is_moderated flag to comments for ML moderation pipeline
ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS is_moderated BOOLEAN NOT NULL DEFAULT false;

-- Index to support future moderation queue queries efficiently
CREATE INDEX IF NOT EXISTS idx_comments_is_moderated ON comments(is_moderated);
CREATE INDEX IF NOT EXISTS idx_comments_parent_id ON comments(parent_comment_id);
