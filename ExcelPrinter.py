import pandas as pd
import re
import os

# --- Excel에서 문제되는 제어 문자 제거 ---
def clean_illegal_chars(text):
    if isinstance(text, str):
        # 엑셀 저장 시 오류를 일으킬 수 있는 비인쇄 문자 제거
        return re.sub(r'[\x00-\x1F\x7F]', '', text)
    return text

file_path = "example.txt"
black_list_file = "ip-black.txt"

# 1. 파일 로드
if not os.path.exists(file_path):
    print(f"❌ {file_path} 없음 - 프로그램을 종료합니다.")
    exit()

with open(file_path, "r", encoding="utf-8") as f:
    lines = f.read().splitlines()

if len(lines) < 2:
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
sum_posts = 0
sum_view = 0
sum_reco = 0
sum_reply = 0
sum_comment = 0

for line in lines[1:]:
    parts = [clean_illegal_chars(x) for x in line.split('SPLIT')]
    # 자바 출력 형식: 이름(0), ID/IP(1), 타입(2), 글(3), 조회(4), 추천(5), 리플(6), 댓글(7)
    if len(parts) < 8: continue

    # 숫자형 변환 (인덱스 3번부터 7번까지)
    for i in range(3, 8):
        parts[i] = int(parts[i]) if str(parts[i]).isdigit() and parts[i] else 0

    sum_posts   += parts[3]
    sum_view    += parts[4]
    sum_reco    += parts[5]
    sum_reply   += parts[6]
    sum_comment += parts[7]

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

for line in lines[1:]:
    parts = [clean_illegal_chars(x) for x in line.split('SPLIT')]
    if len(parts) < 8: continue

    # 블랙리스트 필터링 (ID/IP 기준)
    if any(v in parts[1] for v in filter_values):
        continue

    # 숫자 데이터 정수화
    for i in range(3, 8):
        parts[i] = int(parts[i]) if str(parts[i]).isdigit() and parts[i] else 0

    # 평균치 계산 (parts[3] = 글 수)
    if parts[3] > 0:
        avg_view  = round(parts[4] / parts[3], 2)
        avg_reco  = round(parts[5] / parts[3], 2)
        avg_reply = round(parts[6] / parts[3], 2)
    else:
        avg_view = avg_reco = avg_reply = 0

    # 가중값 계산 (글 수 * 0.56 + 댓글 수 * 0.18)
    calc = round((parts[3] * 0.56 + parts[7] * 0.18), 2)
    if parts[2]=="비고정" or parts[2]=="비회원":
        parts[0] = parts[0]+'('+parts[1]+')'
    if parts[2]=="비회원" and parts[1]=="":
        parts[1]="익명"
    # 데이터 행 구성
    rows.append([
        parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], parts[7],
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

# 편차율 계산
df["조회수 편차율"] = round((df["평균 조회수"] - total_avg_view) / total_avg_view * 100, 2) if total_avg_view != 0 else 0
df["추천 편차율"] = round((df["평균 추천 수"] - total_avg_reco) / total_avg_reco * 100, 2) if total_avg_reco != 0 else 0
df["리플 편차율"] = round((df["평균 리플 수"] - total_avg_reply) / total_avg_reply * 100, 2) if total_avg_reply != 0 else 0

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