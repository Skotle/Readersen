#!/usr/bin/env python3
"""Local, read-only web server for the generated SQLite dataset."""

from __future__ import annotations

import argparse
import json
import mimetypes
import re
import sqlite3
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse


ROOT = Path(__file__).resolve().parent
STATIC_ROOT = ROOT / "static"
DEFAULT_DB = ROOT / "data" / "readersen.sqlite3"
MAX_QUERY_ROWS = 500
MAX_QUERY_LENGTH = 20_000
MAX_BODY_SIZE = 64_000
READ_QUERY = re.compile(r"^(?:SELECT|WITH|EXPLAIN)\b", re.IGNORECASE)


def open_database(db_path: Path) -> sqlite3.Connection:
    uri = f"file:{db_path.as_posix()}?mode=ro"
    connection = sqlite3.connect(uri, uri=True, timeout=2)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA query_only = ON")
    return connection


def parse_limit(value: str | None, default: int = 50, maximum: int = 200) -> int:
    try:
        return max(1, min(int(value or default), maximum))
    except ValueError:
        return default


def parse_offset(value: str | None) -> int:
    try:
        return max(0, int(value or 0))
    except ValueError:
        return 0


class DatasetHandler(BaseHTTPRequestHandler):
    server_version = "ReadersenSQLite/1.0"

    @property
    def db_path(self) -> Path:
        return self.server.db_path  # type: ignore[attr-defined]

    def log_message(self, format: str, *args: object) -> None:
        print(f"[{self.log_date_time_string()}] {format % args}")

    def send_json(self, payload: object, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def send_error_json(self, message: str, status: HTTPStatus) -> None:
        self.send_json({"error": message}, status)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/stats":
            self.handle_stats()
        elif parsed.path in ("/api/posts", "/api/comments"):
            self.handle_rows(parsed.path, parse_qs(parsed.query))
        elif parsed.path == "/api/rankings":
            self.handle_rankings(parse_qs(parsed.query))
        elif parsed.path == "/api/schema":
            self.handle_schema()
        else:
            self.handle_static(parsed.path)

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/api/query":
            self.send_error_json("요청 경로를 찾을 수 없습니다.", HTTPStatus.NOT_FOUND)
            return
        self.handle_query()

    def handle_stats(self) -> None:
        with open_database(self.db_path) as db:
            row = db.execute(
                """SELECT
                       (SELECT count(*) FROM posts) AS posts,
                       (SELECT count(*) FROM comment_events) AS comments,
                       (SELECT count(DISTINCT user_key) FROM activity_events) AS users,
                       (SELECT min(created_at) FROM activity_events) AS date_from,
                       (SELECT max(created_at) FROM activity_events) AS date_to,
                       (SELECT coalesce(sum(view_count), 0) FROM posts) AS views"""
            ).fetchone()
        self.send_json(dict(row))

    def handle_rows(self, path: str, query: dict[str, list[str]]) -> None:
        is_posts = path == "/api/posts"
        table = "posts" if is_posts else "comment_events"
        columns = (
            "id, nickname, user_key, created_at, view_count, recommend_count, comment_count"
            if is_posts
            else "id, nickname, user_key, created_at"
        )
        limit = parse_limit(query.get("limit", [None])[0])
        offset = parse_offset(query.get("offset", [None])[0])
        search = query.get("search", [""])[0].strip()[:100]
        date_from = query.get("from", [""])[0].strip()
        date_to = query.get("to", [""])[0].strip()

        clauses: list[str] = []
        params: list[object] = []
        if search:
            clauses.append("(nickname LIKE ? OR user_key LIKE ?)")
            wildcard = f"%{search}%"
            params.extend((wildcard, wildcard))
        if date_from:
            clauses.append("created_at >= ?")
            params.append(date_from)
        if date_to:
            clauses.append("created_at <= ?")
            params.append(date_to)

        where = " WHERE " + " AND ".join(clauses) if clauses else ""
        with open_database(self.db_path) as db:
            total = db.execute(f"SELECT count(*) FROM {table}{where}", params).fetchone()[0]
            rows = db.execute(
                f"SELECT {columns} FROM {table}{where} "
                "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                [*params, limit, offset],
            ).fetchall()
        self.send_json({"total": total, "rows": [dict(row) for row in rows]})

    def handle_rankings(self, query: dict[str, list[str]]) -> None:
        limit = parse_limit(query.get("limit", [None])[0], default=10, maximum=50)
        date_from = query.get("from", [""])[0].strip()
        date_to = query.get("to", [""])[0].strip()

        clauses: list[str] = []
        params: list[object] = []
        if date_from:
            clauses.append("created_at >= ?")
            params.append(date_from)
        if date_to:
            clauses.append("created_at <= ?")
            params.append(date_to)
        where = " WHERE " + " AND ".join(clauses) if clauses else ""

        try:
            with open_database(self.db_path) as db:
                rows = db.execute(
                f"""WITH activity AS (
                        SELECT
                            user_key,
                            nickname,
                            COUNT(*) AS post_count,
                            0 AS comment_count,
                            COALESCE(SUM(view_count), 0) AS view_count,
                            COALESCE(SUM(recommend_count), 0) AS recommend_count,
                            COALESCE(SUM(comment_count), 0) AS reply_count
                        FROM posts{where}
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
                        FROM comment_events{where}
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
                    LIMIT ?""",
                [*params, *params, limit],
                ).fetchall()
        except sqlite3.Error as error:
            self.log_message("Ranking query error: %s", str(error))
            self.send_error_json(str(error), HTTPStatus.BAD_REQUEST)
            return

        self.send_json({"rows": [dict(row) for row in rows]})

    def handle_schema(self) -> None:
        with open_database(self.db_path) as db:
            rows = db.execute(
                """SELECT type, name, sql
                   FROM sqlite_master
                   WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%'
                   ORDER BY type, name"""
            ).fetchall()
        self.send_json({"objects": [dict(row) for row in rows]})

    def handle_query(self) -> None:
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            content_length = 0
        if content_length <= 0 or content_length > MAX_BODY_SIZE:
            self.send_error_json("요청 본문 크기가 올바르지 않습니다.", HTTPStatus.BAD_REQUEST)
            return

        try:
            payload = json.loads(self.rfile.read(content_length))
            sql = payload.get("sql", "").strip()
        except (json.JSONDecodeError, AttributeError):
            self.send_error_json("JSON 요청을 해석할 수 없습니다.", HTTPStatus.BAD_REQUEST)
            return

        if not sql or len(sql) > MAX_QUERY_LENGTH or not READ_QUERY.match(sql):
            self.send_error_json(
                "SELECT, WITH 또는 EXPLAIN 읽기 쿼리만 실행할 수 있습니다.",
                HTTPStatus.BAD_REQUEST,
            )
            return

        started = time.perf_counter()
        try:
            with open_database(self.db_path) as db:
                db.set_progress_handler(
                    lambda: 1 if time.perf_counter() - started > 3 else 0, 10_000
                )
                cursor = db.execute(sql)
                if cursor.description is None:
                    raise sqlite3.OperationalError("결과 열이 없는 쿼리입니다.")
                columns = [column[0] for column in cursor.description]
                fetched = cursor.fetchmany(MAX_QUERY_ROWS + 1)
                truncated = len(fetched) > MAX_QUERY_ROWS
                rows = [list(row) for row in fetched[:MAX_QUERY_ROWS]]
        except sqlite3.Error as error:
            self.send_error_json(str(error), HTTPStatus.BAD_REQUEST)
            return

        elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
        self.send_json(
            {
                "columns": columns,
                "rows": rows,
                "rowCount": len(rows),
                "truncated": truncated,
                "elapsedMs": elapsed_ms,
            }
        )

    def handle_static(self, request_path: str) -> None:
        relative = "index.html" if request_path == "/" else unquote(request_path.lstrip("/"))
        candidate = (STATIC_ROOT / relative).resolve()
        if STATIC_ROOT.resolve() not in candidate.parents and candidate != STATIC_ROOT.resolve():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        if not candidate.is_file():
            self.send_error(HTTPStatus.NOT_FOUND)
            return

        content = candidate.read_bytes()
        content_type = mimetypes.guess_type(candidate.name)[0] or "application/octet-stream"
        if content_type.startswith("text/") or content_type in ("application/javascript", "application/json"):
            content_type += "; charset=utf-8"
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(content)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Readersen SQLite 로컬 웹 조회기")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    db_path = args.db.resolve()
    if not db_path.is_file():
        print(f"DB를 찾을 수 없습니다: {db_path}")
        print("먼저 convert_to_sqlite.py를 실행하세요.")
        return 1

    server = ThreadingHTTPServer((args.host, args.port), DatasetHandler)
    server.db_path = db_path  # type: ignore[attr-defined]
    print(f"Readersen SQLite 조회기: http://{args.host}:{args.port}")
    print(f"DB: {db_path}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n서버를 종료합니다.")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
