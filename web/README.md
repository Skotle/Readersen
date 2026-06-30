# Readersen SQLite 데이터 조회기

두 원본 TXT 파일을 SQLite로 변환하고, 로컬 웹페이지에서 탐색하거나 읽기 전용 SQL을 실행하는 도구입니다. 외부 Python 패키지는 필요하지 않습니다.

## 빠른 실행

PowerShell에서 다음을 실행합니다.

```powershell
.\start.ps1
```

브라우저에서 `http://127.0.0.1:8000`을 엽니다. SQL 콘솔은 상단의 **SQL 도구**에서 별도로 접근할 수 있습니다.

## 변환 스크립트

원본이 변경되었을 때 DB만 다시 만들려면:

```powershell
python .\convert_to_sqlite.py --force
```

Python 경로를 직접 지정하는 예:

```powershell
& "C:\path\to\python.exe" .\convert_to_sqlite.py --force
```

기본 입출력:

- 게시 원본: `daily-data.txt`
- 댓글 원본: `daily-data-comment.txt`
- SQLite 출력: `data/readersen.sqlite3`

다른 파일은 `--posts`, `--comments`, `--output` 옵션으로 지정할 수 있습니다. 변환은 임시 DB에 완료한 후 원자적으로 교체되며, 모든 행의 서식과 SQLite 무결성을 검사합니다.

## 데이터 구조

- `posts`: 닉네임, 사용자 식별자, 작성 시각, 조회/추천/댓글 수
- `comment_events`: 닉네임, 사용자 식별자, 작성 시각
- `activity_events`: 두 활동을 합친 읽기 전용 뷰
- `daily_summary`: 날짜별 게시/댓글/활성 사용자 집계 뷰
- `dataset_meta`: 생성 시각과 원본·레코드 수 메타데이터

웹 SQL API는 `SELECT`, `WITH`, `EXPLAIN`만 허용하며 최대 500행, 최대 3초로 제한됩니다. 서버는 기본적으로 로컬 주소(`127.0.0.1`)에만 열립니다.
