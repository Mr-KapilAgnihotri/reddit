-- ─────────────────────────────────────────────────────────────────────────────
-- V14 : Resize posts.embedding from vector(768) → vector(384)
--       and add IVFFlat cosine similarity index
-- ─────────────────────────────────────────────────────────────────────────────
-- Altering the dimension of an existing vector column raises a cast error on
-- PostgreSQL (even with NULL data). The safe approach per the pgvector docs is
-- to DROP the column and ADD it fresh. Embeddings are re-generated asynchronously
-- by the ML service via Kafka — no data loss for functional data.

ALTER TABLE posts DROP COLUMN IF EXISTS embedding;
ALTER TABLE posts ADD COLUMN embedding vector(384);

-- IVFFlat index for approximate cosine similarity search.
-- lists=10 is safe when the table has few rows (as in dev).
-- For production (>1M rows) use lists=100.
CREATE INDEX IF NOT EXISTS idx_posts_embedding_cosine
    ON posts
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);

-- Add is_moderated column to posts if not already present (V7 may have done it)
-- This is a no-op if the column exists.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS is_moderated BOOLEAN NOT NULL DEFAULT FALSE;
