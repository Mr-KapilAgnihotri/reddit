-- Enable vector extension (AI ready)
CREATE EXTENSION IF NOT EXISTS vector;

-- USERS
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_role CHECK (role IN ('USER','ADMIN'))
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);


-- USER PROFILES
CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY,
    display_name VARCHAR(50),
    bio TEXT,
    avatar_url TEXT,
    preferences JSONB DEFAULT '{}',
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);


-- COMMUNITIES
CREATE TABLE communities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_by BIGINT,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE SET NULL
);


-- COMMUNITY MEMBERS
CREATE TABLE community_members (
    user_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    joined_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, community_id),
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    FOREIGN KEY (community_id)
        REFERENCES communities(id)
        ON DELETE CASCADE
);


-- POSTS
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    community_id BIGINT,
    author_id BIGINT NOT NULL,
    title VARCHAR(300) NOT NULL,
    original_text TEXT,
    display_text TEXT,
    upvotes BIGINT NOT NULL DEFAULT 0,
    downvotes BIGINT NOT NULL DEFAULT 0,
    score BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    hot_score DOUBLE PRECISION,
    embedding vector(768),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (community_id)
        REFERENCES communities(id)
        ON DELETE SET NULL,
    FOREIGN KEY (author_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_posts_hot_score ON posts(hot_score DESC);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX idx_posts_community_created ON posts(community_id, created_at DESC);


-- COMMENTS
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    parent_comment_id BIGINT,
    author_id BIGINT NOT NULL,
    original_text TEXT,
    display_text TEXT,
    upvotes BIGINT NOT NULL DEFAULT 0,
    downvotes BIGINT NOT NULL DEFAULT 0,
    score BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (post_id)
        REFERENCES posts(id)
        ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id)
        REFERENCES comments(id)
        ON DELETE CASCADE,
    FOREIGN KEY (author_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_comments_post_id ON comments(post_id);


-- POST VOTES
CREATE TABLE post_votes (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    value SMALLINT NOT NULL CHECK (value IN (1, -1)),
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, post_id),
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    FOREIGN KEY (post_id)
        REFERENCES posts(id)
        ON DELETE CASCADE
);


-- COMMENT VOTES
CREATE TABLE comment_votes (
    user_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    value SMALLINT NOT NULL CHECK (value IN (1, -1)),
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, comment_id),
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    FOREIGN KEY (comment_id)
        REFERENCES comments(id)
        ON DELETE CASCADE
);


-- FOLLOWS
CREATE TABLE follows (
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    FOREIGN KEY (follower_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    FOREIGN KEY (followee_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> followee_id)
);


-- SAVED POSTS
CREATE TABLE saved_posts (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    saved_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, post_id),
    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    FOREIGN KEY (post_id)
        REFERENCES posts(id)
        ON DELETE CASCADE
);


-- MODERATION FLAGS
CREATE TABLE moderation_flags (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    severity DOUBLE PRECISION,
    categories JSONB,
    flagged_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT chk_target_type CHECK (target_type IN ('POST','COMMENT'))
);