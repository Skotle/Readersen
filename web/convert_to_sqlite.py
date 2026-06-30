#!/usr/bin/env python3
"""Convert the bracket-delimited source files into an atomic SQLite dataset."""

from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Iterator, Sequence


ROOT = Path(__file__).resolve().parent
POST_PATTERN = re.compile(
    r"^\[(.*?)\] \[(.*?)\] "
    r"\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\] "
    r"\[(\d+)\] \[(\d+)\] \[(\d+)\]$"
)
COMMENT_PATTERN = re.compile(
    r"^\[(.*?)\] \[(.*?)\] "
    r"\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]$"
)
BATCH_SIZE = 1_000


class SourceFormatError(ValueError):
    pass


def iter_parsed(path: Path, pattern: re.Pattern[str], numeric_from: int) -> Iterator[tuple]:
    """Yield parsed fields plus their source line, failing on the first malformed row."""
    with path.open("r", encoding="utf-8-sig", newline=None) as source:
        for line_number, raw_line in enumerate(source, 1):
            line = raw_line.rstrip("\r\n")
            match = pattern.fullmatch(line)
            if not match:
                preview = line if len(line) <= 120 else line[:117] + "..."
                raise SourceFormatError(
                    f"{path.name}:{line_number}: 형식이 올바르지 않습니다: {preview!r}"
                )
            fields: list[object] = list(match.groups())
            for index in range(numeric_from, len(fields)):
                fields[index] = int(fields[index])
            fields.append(line_number)
            yield tuple(fields)


def batched(rows: Iterable[tuple], size: int = BATCH_SIZE) -> Iterator[list[tuple]]:
    batch: list[tuple] = []
    for row in rows:
        batch.append(row)
        if len(batch) == size:
            yield batch
            batch = []
    if batch:
        yield batch


def insert_rows(
    connection: sqlite3.Connection,
    sql: str,
    rows: Iterable[tuple],
) -> int:
    count = 0
    for batch in batched(rows):
        connection.executemany(sql, batch)
        count += len(batch)
    return count


def convert(posts_path: Path, comments_path: Path, output_path: Path, force: bool) -> None:
    for source in (posts_path, comments_path):
        if not source.is_file():
            raise FileNotFoundError(f"원본 파일을 찾을 수 없습니다: {source}")

    if output_path.exists() and not force:
        raise FileExistsError(
            f"출력 DB가 이미 존재합니다: {output_path} (덮어쓰려면 --force 사용)"
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = output_path.with_name(output_path.name + ".tmp")
    temporary_path.unlink(missing_ok=True)

    try:
        connection = sqlite3.connect(temporary_path)
        try:
            connection.execute("PRAGMA journal_mode = OFF")
            connection.execute("PRAGMA synchronous = OFF")
            connection.executescript((ROOT / "schema.sql").read_text(encoding="utf-8"))

            post_count = insert_rows(
                connection,
                """INSERT INTO posts
                   (nickname, user_key, created_at, view_count,
                    recommend_count, comment_count, source_line)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                iter_parsed(posts_path, POST_PATTERN, numeric_from=3),
            )
            comment_count = insert_rows(
                connection,
                """INSERT INTO comment_events
                   (nickname, user_key, created_at, source_line)
                   VALUES (?, ?, ?, ?)""",
                iter_parsed(comments_path, COMMENT_PATTERN, numeric_from=3),
            )

            generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
            metadata: Sequence[tuple[str, str]] = (
                ("generated_at_utc", generated_at),
                ("posts_source", posts_path.name),
                ("comments_source", comments_path.name),
                ("post_count", str(post_count)),
                ("comment_event_count", str(comment_count)),
                ("schema_version", "1"),
            )
            connection.executemany(
                "INSERT INTO dataset_meta(key, value) VALUES (?, ?)", metadata
            )
            connection.commit()
            result = connection.execute("PRAGMA integrity_check").fetchone()[0]
            if result != "ok":
                raise RuntimeError(f"SQLite 무결성 검사 실패: {result}")
        finally:
            connection.close()

        os.replace(temporary_path, output_path)
        print(f"변환 완료: {output_path}")
        print(f"  posts: {post_count:,}건")
        print(f"  comment_events: {comment_count:,}건")
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Readersen TXT 데이터셋을 SQLite로 변환")
    parser.add_argument("--posts", type=Path, default=ROOT / "daily-data.txt")
    parser.add_argument(
        "--comments", type=Path, default=ROOT / "daily-data-comment.txt"
    )
    parser.add_argument(
        "--output", type=Path, default=ROOT / "data" / "readersen.sqlite3"
    )
    parser.add_argument("--force", action="store_true", help="기존 출력 DB 덮어쓰기")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        convert(args.posts.resolve(), args.comments.resolve(), args.output.resolve(), args.force)
    except (OSError, sqlite3.Error, SourceFormatError, RuntimeError) as error:
        print(f"오류: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
