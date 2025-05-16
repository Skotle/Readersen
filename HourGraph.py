import matplotlib.pyplot as plt
from collections import Counter

# 파일 읽기
with open('time-data.txt', 'r') as file:
    lines = file.readlines()

# 시(hour)만 추출
hours = [line.strip().split(":")[0] for line in lines if line.strip()]

# 빈도수 계산
hour_counts = Counter(hours)

# 전체 개수
total = sum(hour_counts.values())

# 시각화를 위한 정렬
sorted_hours = sorted(hour_counts.keys())
percentages = [(hour_counts[hour] / total) * 100 for hour in sorted_hours]

# 그래프 그리기
plt.figure(figsize=(10, 6))
bars = plt.bar(sorted_hours, percentages, color='lightgreen')
plt.xlabel('Hour of Day')
plt.ylabel('Percentage (%)')
plt.title('Percentage of Times by Hour')
plt.ylim(0, max(percentages) + 5)
plt.grid(axis='y', linestyle='--', alpha=0.7)

# 백분율 수치 표시 (소수점 1자리)
for bar, pct in zip(bars, percentages):
    plt.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.5,
             f"{pct:.1f}%", ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.show()

