PRAGMA foreign_keys = ON;

CREATE TABLE dataset_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
) STRICT;

CREATE TABLE posts (
    id INTEGER PRIMARY KEY,
    nickname TEXT NOT NULL,
    user_key TEXT NOT NULL,
    created_at TEXT NOT NULL,
    view_count INTEGER NOT NULL CHECK (view_count >= 0),
    recommend_count INTEGER NOT NULL CHECK (recommend_count >= 0),
    comment_count INTEGER NOT NULL CHECK (comment_count >= 0),
    source_line INTEGER NOT NULL UNIQUE CHECK (source_line > 0)
) STRICT;

CREATE TABLE comment_events (
    id INTEGER PRIMARY KEY,
    nickname TEXT NOT NULL,
    user_key TEXT NOT NULL,
    created_at TEXT NOT NULL,
    source_line INTEGER NOT NULL UNIQUE CHECK (source_line > 0)
) STRICT;

CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX idx_posts_user_key ON posts(user_key);
CREATE INDEX idx_posts_nickname ON posts(nickname);
CREATE INDEX idx_comment_events_created_at ON comment_events(created_at DESC);
CREATE INDEX idx_comment_events_user_key ON comment_events(user_key);
CREATE INDEX idx_comment_events_nickname ON comment_events(nickname);

CREATE VIEW activity_events AS
SELECT
    'post' AS event_type,
    id AS event_id,
    nickname,
    user_key,
    created_at,
    view_count,
    recommend_count,
    comment_count
FROM posts
UNION ALL
SELECT
    'comment' AS event_type,
    id AS event_id,
    nickname,
    user_key,
    created_at,
    NULL AS view_count,
    NULL AS recommend_count,
    NULL AS comment_count
FROM comment_events;

CREATE VIEW daily_summary AS
SELECT
    substr(created_at, 1, 10) AS activity_date,
    sum(event_type = 'post') AS post_count,
    sum(event_type = 'comment') AS comment_count,
    count(DISTINCT user_key) AS active_users
FROM activity_events
GROUP BY substr(created_at, 1, 10);
