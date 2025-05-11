from datetime import datetime
from collections import Counter
import numpy as np
import matplotlib.pyplot as plt

# Java에서 저장한 파일 읽기
with open("date-data.txt", "r", encoding="utf-8") as f:
    raw_dates = [line.strip() for line in f if line.strip()]

now = datetime.now()
current_year = now.year

# 날짜 변환 함수
def correct_and_format_date(d):
    if ":" in d:
        d_obj = now
    else:
        try:
            d_obj = datetime.strptime(d, "%m.%d").replace(year=current_year)
            if d_obj > now:
                d_obj = d_obj.replace(year=current_year - 1)
        except ValueError:
            return None
    # 포맷 처리: 올해면 "MM.DD", 작년 등 과거면 "YY.MM.DD"
    return d_obj.strftime("%m.%d") if d_obj.year == current_year else d_obj.strftime("%y.%m.%d")

# 날짜 처리 및 카운팅
processed_days = list(filter(None, [correct_and_format_date(d) for d in raw_dates]))
counter = Counter(processed_days)
counter = {k: v for k, v in counter.items() if v >= 30}

if counter:
    counts = list(counter.values())
    average = sum(counts) / len(counts)
    filtered_counter = {k: v for k, v in counter.items() if v >= average * 0.30}

    if filtered_counter:
        dates = sorted(filtered_counter)
        counts = [filtered_counter[d] for d in dates]
        cumulative_counts = np.cumsum(counts)

        fig, ax1 = plt.subplots(figsize=(12, 5))

        bars = ax1.bar(range(len(dates)), counts, color='skyblue', label='Daily Count')
        ax1.set_ylabel("Daily Count", color='skyblue')
        ax1.tick_params(axis='y', labelcolor='skyblue')
        ax1.axhline(y=average, color='red', linestyle='--', label=f'Average ({average:.1f})')

        for i, bar in enumerate(bars):
            height = bar.get_height()
            ax1.text(bar.get_x() + bar.get_width() * 0.3, height + 2, f'{int(height)}',
                     ha='center', va='bottom', fontsize=9, color='blue')

        ax2 = ax1.twinx()
        ax2.plot(range(len(dates)), cumulative_counts, marker='o', color='green', label='Cumulative Count')
        ax2.set_ylabel("Cumulative Count", color='green')
        ax2.tick_params(axis='y', labelcolor='green')

        interval = max(1, len(dates) // 10)
        for i, y in enumerate(cumulative_counts):
            if i % interval == 0 or i == len(dates) - 1:
                offset = 0.35 if i == len(dates) - 1 else 0.15
                ax2.text(i + offset, y + 5, f'{int(y)}', ha='center', va='bottom', fontsize=9, color='green')

        ax1.set_xticks(range(len(dates)))
        xtick_labels = [date if i % interval == 0 else '' for i, date in enumerate(dates)]
        ax1.set_xticklabels(xtick_labels, rotation=0, ha='center', fontsize=9)

        lines1, labels1 = ax1.get_legend_handles_labels()
        lines2, labels2 = ax2.get_legend_handles_labels()
        ax1.legend(lines1 + lines2, labels1 + labels2, loc='upper left')

        plt.title("Daily vs. Cumulative Activity (with Year-Aware Format)")
        plt.tight_layout()
        plt.show()
    else:
        print("평균의 30% 이상인 날짜가 없습니다.")
else:
    print("30개 이상인 날짜가 없습니다.")
