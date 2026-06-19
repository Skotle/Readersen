import pandas as pd
import json
import re
import os

# --- Excel에서 문제되는 제어 문자 제거 ---
def clean_illegal_chars(text):
    if isinstance(text, str):
        return re.sub(r'[\x00-\x1F\x7F]', '', text)
    return text

file_path = "example.json"
black_list_file = "ip-black.txt"

# 1. 파일 로드
if not os.path.exists(file_path):
    print(f"❌ {file_path} 없음 - 프로그램을 종료합니다.")
    exit()

with open(file_path, "r", encoding="utf-8") as f:
    data = json.load(f)

if not data:
    print("❌ 데이터가 부족합니다.")
    exit()

# ============================================================
# 🔥 필터(블랙리스트) 파일 읽기
# ============================================================
filter_values = []
if os.path.exists(black_list_file):
    with open(black_list_file, "r", encoding="utf-8") as f:
        readip = f.read().splitlines()
    filter_values = [re.sub(r"\[.*?\]", "", line).strip() for line in readip if line.strip()]


# ============================================================
# 🔥 1회차: 전체 통계 계산 (필터링 전 원본 기준)
# ============================================================
sum_posts   = 0
sum_view    = 0
sum_reco    = 0
sum_reply   = 0
sum_comment = 0

for entry in data:
    sum_posts   += entry.get("num",   0)
    sum_view    += entry.get("view",  0)
    sum_reco    += entry.get("recom", 0)
    sum_reply   += entry.get("reple", 0)
    sum_comment += entry.get("comm",  0)

print("\n===== 📊 전체 집계 현황 (필터링 전) =====")
print(f"총 게시글 수  = {sum_posts}")
print(f"총 조회수     = {sum_view}")
print(f"총 추천수     = {sum_reco}")
print(f"총 리플수     = {sum_reply}")
print(f"총 댓글수     = {sum_comment}")


# ============================================================
# 🔥 2회차: 데이터프레임 생성 및 계산
# ============================================================
rows = []

for entry in data:
    name     = clean_illegal_chars(str(entry.get("name",      "")))
    id_or_ip = clean_illegal_chars(str(entry.get("id_or_ip",  "")))
    nicktype = clean_illegal_chars(str(entry.get("nicktype",  "")))
    num      = int(entry.get("num",   0))
    view     = int(entry.get("view",  0))
    recom    = int(entry.get("recom", 0))
    reple    = int(entry.get("reple", 0))
    comm     = int(entry.get("comm",  0))

    # 블랙리스트 필터링 (ID/IP 기준)
    if any(v in id_or_ip for v in filter_values):
        continue

    # 평균치 계산
    if num > 0:
        avg_view  = round(view  / num, 2)
        avg_reco  = round(recom / num, 2)
        avg_reply = round(reple / num, 2)
    else:
        avg_view = avg_reco = avg_reply = 0

    # 가중값 계산 (글 수 * 0.56 + 댓글 수 * 0.18)
    calc = round((num * 0.56 + comm * 0.18), 2)

    # 비고정/비회원 이름 처리
    if nicktype in ("비고정", "비회원"):
        name = name + '(' + id_or_ip + ')'
    if nicktype == "비회원" and id_or_ip == "":
        id_or_ip = "익명"

    rows.append([
        name, id_or_ip, nicktype, num, view, recom, reple, comm,
        avg_view, avg_reco, avg_reply, calc
    ])

# DataFrame 생성
df = pd.DataFrame(rows, columns=[
    "이름", "ID/IP", "타입", "글", "조회수", "추천", "리플", "댓글",
    "평균 조회수", "평균 추천 수", "평균 리플 수", "가중값"
])

# 편차율 계산을 위한 전체 평균 산출
total_avg_view  = df["평균 조회수"].mean()
total_avg_reco  = df["평균 추천 수"].mean()
total_avg_reply = df["평균 리플 수"].mean()

df["조회수 편차율"] = round((df["평균 조회수"]  - total_avg_view)  / total_avg_view  * 100, 2) if total_avg_view  != 0 else 0
df["추천 편차율"]   = round((df["평균 추천 수"]  - total_avg_reco)  / total_avg_reco  * 100, 2) if total_avg_reco  != 0 else 0
df["리플 편차율"]   = round((df["평균 리플 수"]  - total_avg_reply) / total_avg_reply * 100, 2) if total_avg_reply != 0 else 0

# 최종 컬럼 순서 정리
df_final = df[[
    "이름", "ID/IP", "타입", "글", "조회수", "추천", "리플", "댓글",
    "평균 조회수", "조회수 편차율",
    "평균 추천 수", "추천 편차율",
    "평균 리플 수", "리플 편차율",
    "가중값"
]]

# 파일 저장
try:
    df_final.to_excel("user-data.xlsx", index=False, engine='openpyxl')
    df_final.to_csv("user-data.csv", index=False, encoding="utf-8-sig")
    print("\n✅ user-data.xlsx / user-data.csv 저장 완료!")
except Exception as e:
    print(f"\n❌ 저장 실패: {e}")