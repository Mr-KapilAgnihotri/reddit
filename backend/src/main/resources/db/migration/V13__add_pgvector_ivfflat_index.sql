-- ─────────────────────────────────────────────────────────────────────────────
-- V13 : PGVector IVFFlat index on posts.embedding for fast cosine similarity
-- ─────────────────────────────────────────────────────────────────────────────
-- The vector(768) column already exists from V1. Before we can create an
-- IVFFlat index, we must first resize the column to 384 dimensions to match
-- the all-MiniLM-L6-v2 embedding model used by the ML service.
-- That resize happens in V14 (which DROPs and re-ADDs the column so existing
-- NULL embeddings don't raise a cast error).
-- This migration just creates the index after V14 has run.
-- PGVector cosine index — requires >= 1 vector inserted before IVFFlat
-- training; we use CREATE INDEX CONCURRENTLY is not allowed in transactions,
-- so we use a plain CREATE INDEX.  The ML service will populate embeddings
-- asynchronously via Kafka.

-- Saved-posts index for fast feed queries
CREATE INDEX IF NOT EXISTS idx_saved_posts_user_id ON saved_posts(user_id);

-- IVFFlat index will be created in V14 after column type is correct
