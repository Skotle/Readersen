from pathlib import Path
import sqlite3

DB = Path(__file__).resolve().parent / 'data' / 'readersen.sqlite3'
print('DB:', DB)
conn = sqlite3.connect(DB.as_posix())
conn.row_factory = sqlite3.Row

sql = '''WITH activity AS (
    SELECT
        user_key,
        nickname,
        COUNT(*) AS post_count,
        0 AS comment_count,
        COALESCE(SUM(view_count), 0) AS view_count,
        COALESCE(SUM(recommend_count), 0) AS recommend_count,
        COALESCE(SUM(comment_count), 0) AS reply_count
    FROM posts
    GROUP BY user_key, nickname
    UNION ALL
    SELECT
        user_key,
        nickname,
        0 AS post_count,
        COUNT(*) AS comment_count,
        0 AS view_count,
        0 AS recommend_count,
        0 AS reply_count
    FROM comment_events
    GROUP BY user_key, nickname
),
grouped AS (
    SELECT
        user_key,
        MAX(nickname) AS nickname,
        SUM(post_count) AS post_count,
        SUM(comment_count) AS comment_count,
        SUM(post_count) + SUM(comment_count) AS activity_count,
        SUM(view_count) AS view_count,
        SUM(recommend_count) AS recommend_count,
        SUM(reply_count) AS reply_count
    FROM activity
    GROUP BY user_key
)
SELECT
    ROW_NUMBER() OVER (
        ORDER BY activity_count DESC, post_count DESC, comment_count DESC, view_count DESC, user_key
    ) AS rank,
    user_key,
    nickname,
    post_count,
    comment_count,
    activity_count,
    view_count,
    recommend_count,
    reply_count
FROM grouped
ORDER BY activity_count DESC, post_count DESC, comment_count DESC, view_count DESC, user_key
LIMIT 5
'''

try:
    cur = conn.execute(sql)
    rows = cur.fetchall()
    print('rows:', len(rows))
    for r in rows:
        print(dict(r))
except sqlite3.Error as e:
    print('sqlite error:', e)
finally:
    conn.close()
