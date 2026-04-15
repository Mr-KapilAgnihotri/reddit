CREATE TABLE post_media (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    media_url TEXT NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    caption TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),

    FOREIGN KEY (post_id)
        REFERENCES posts(id)
        ON DELETE CASCADE
);