from datetime import datetime, timedelta
from collections import Counter
import numpy as np
import matplotlib.pyplot as plt

# === 모드 설정 ===
# 선택: "daily", "weekly", "monthly"
MODE = "weekly"

# === 파일 읽기 ===
try:
    with open("date-data.txt", "r", encoding="utf-8") as f:
        raw_lines = [line.strip() for line in f if line.strip()]
except FileNotFoundError:
    print("date-data.txt 파일을 찾을 수 없습니다.")
    exit()

dates = []
hours = []

# === 날짜/시간 파싱 ===
for line in raw_lines:
    try:
        dt = datetime.strptime(line, "%Y-%m-%d %H:%M:%S")
        dates.append(dt)
        hours.append(dt.hour)
    except:
        pass

if not dates:
    print("데이터 없음")
    exit()

now = datetime.now()
current_year = now.year

# === 날짜 라벨 함수 (일간 모드용) ===
def date_label(dt):
    if dt.year < current_year:
        return dt.strftime("%y-%m-%d") # %Y(2023) -> %y(23)
    return dt.strftime("%m-%d")

# === 표준 주차 라벨링 함수 (이전 연도에만 'YY' 연도 적용) ===
def format_standard_week_label(dt):
    """
    주어진 날짜가 속한 주의 라벨을 생성합니다.
    이전 연도 데이터가 포함된 경우에만 자동으로 YY.MM.DD 형식을 사용합니다.
    """
    # 1. 해당 주의 시작일(월) 및 끝일(일) 계산
    start_of_week_date = dt.date() - timedelta(days=dt.weekday())
    end_of_week_date = start_of_week_date + timedelta(days=6)
    
    # 2. 연도 표시 로직 결정
    # 시작일이나 종료일 중 하나라도 현재 연도보다 이전이면 연도(YY) 표시
    if start_of_week_date.year < current_year or end_of_week_date.year < current_year:
        # %Y. 대신 %y. 사용하여 2023 -> 23으로 변경
        start_label = start_of_week_date.strftime("%y.%m.%d")
        end_label = end_of_week_date.strftime("%y.%m.%d")
    else:
        # 올해 데이터인 경우 월.일만 표시
        start_label = start_of_week_date.strftime("%m.%d")
        end_label = end_of_week_date.strftime("%m.%d")
        
    label = f"{start_label} ~ {end_label}"
    return start_of_week_date, label

# === 오늘 기준으로 역순 정렬을 위한 키 계산 함수 ===
def get_week_sort_key_for_backward_order(start_of_week_date):
    today_date = now.date()
    current_week_start = today_date - timedelta(days=today_date.weekday())
    diff = start_of_week_date - current_week_start
    return diff.total_seconds()

# ============================================================
# 1) 모드 선택: 일간 / 주간 / 월간 (카운트 집계)
# ============================================================

if MODE == "daily":
    print("📌 일간 모드")
    counter = Counter([date_label(dt) for dt in dates])

elif MODE == "weekly":
    print("📌 주간 모드 (이전 연도 YY 변환 및 역순 정렬)")
    week_counter = Counter()
    label_map = {}
    
    for dt in dates:
        sort_key_date, week_label = format_standard_week_label(dt)
        final_sort_key = get_week_sort_key_for_backward_order(sort_key_date)
        week_counter[final_sort_key] += 1
        label_map[final_sort_key] = week_label
    counter = week_counter 

elif MODE == "monthly":
    print("📌 월간 모드")
    monthly_labels = []
    for dt in dates:
        if dt.year < current_year:
            monthly_labels.append(dt.strftime("%y-%m")) # %Y-%m -> %y-%m
        else:
            monthly_labels.append(dt.strftime("%m"))
    counter = Counter(monthly_labels)

else:
    print("MODE 값이 잘못됨")
    exit()

# ============================================================
# 2) 이상값 (Outlier) 제거
# ============================================================
counts = list(counter.values())

if counts:
    initial_average = sum(counts) / len(counts)
    threshold = initial_average * 0.1
    
    print(f"평균: {initial_average:.2f}, 이상값 임계치 (10%): {threshold:.2f}")

    keys_to_remove = [key for key, count in counter.items() if count < threshold]

    if keys_to_remove:
        for key in keys_to_remove:
            display_key = label_map.get(key, key) if MODE == "weekly" else key
            print(f"  - 제외: {display_key} ({counter[key]}회)")
            del counter[key]
        
    counts = list(counter.values())
    average = sum(counts) / len(counts) if counts else 0
else:
    print("집계된 데이터 없음.")
    exit()

# ============================================================
# 3) 그래프 생성
# ============================================================
sorted_keys = sorted(counter.keys())

if MODE == "weekly":
    x = [label_map[key] for key in sorted_keys]
    c = [counter[key] for key in sorted_keys]
else:
    x = sorted_keys
    c = [counter[key] for key in x]
    
cumulative = np.cumsum(c)

fig, ax1 = plt.subplots(figsize=(12, 5))
bars = ax1.bar(range(len(x)), c, color='skyblue', alpha=0.8)

# 평균선
ax1.axhline(y=average, color='red', linestyle='--', linewidth=1, label=f"Average: {average:.1f}")

# 막대 위 숫자 표시
for i, v in enumerate(c):
    ax1.text(i, v + 0.1, str(v), ha='center', fontsize=8)

# 누적 선
ax2 = ax1.twinx()
ax2.plot(range(len(x)), cumulative, marker="o", color='orange', label="Cumulative")

ax1.set_xticks(range(len(x)))
ax1.set_xticklabels(x, rotation=45, ha="right", fontsize=9)

plt.title(f"Activity Statistics ({MODE}) - YY.MM.DD Format")
plt.tight_layout()
plt.show()

# ============================================================
# 4) 시간대별 통계 및 콘솔 출력
# ============================================================
hour_counter = Counter(hours)
total_hours = sum(hour_counter.values())

print("\n===== 📌 선택된 기간 통계 (이상값 제거 후) =====")
if MODE == "weekly":
    for k_sort in sorted_keys:
        print(f"{label_map[k_sort]:<25} → {counter[k_sort]}회")
else:
    for k in sorted_keys:
        print(f"{k:<25} → {counter[k]}회")

print("\n===== 📌 시간대별 통계 (전체 데이터 기준) =====")
for h in range(24):
    v = hour_counter.get(h, 0)
    p = (v / total_hours * 100) if total_hours else 0
    print(f"{str(h).zfill(2)}시 → {p:.1f}% ({v}회)")