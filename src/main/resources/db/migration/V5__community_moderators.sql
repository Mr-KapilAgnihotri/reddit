CREATE TABLE community_moderators (
    community_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_at TIMESTAMPTZ DEFAULT now(),

    PRIMARY KEY (community_id, user_id),

    FOREIGN KEY (community_id)
        REFERENCES communities(id)
        ON DELETE CASCADE,

    FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);