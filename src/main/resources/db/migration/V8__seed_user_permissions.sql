-- Add POST_VOTE and COMMENT_VOTE permissions
INSERT INTO permissions (name) VALUES ('POST_VOTE') ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('COMMENT_VOTE') ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('POST_UPDATE') ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('COMMENT_UPDATE') ON CONFLICT (name) DO NOTHING;

-- Grant all standard user permissions to the USER role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'USER'
  AND p.name IN ('USER_READ', 'POST_CREATE', 'POST_UPDATE', 'POST_VOTE', 'COMMENT_CREATE', 'COMMENT_VOTE', 'COMMENT_UPDATE')
ON CONFLICT DO NOTHING;
