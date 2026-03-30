import pandas as pd
import bar_chart_race as bcr
import re
import matplotlib.pyplot as plt
from matplotlib import font_manager, rc

# 1. Matplotlib 한글 폰트 설정 (Windows 기준)
# 이 설정이 bar_chart_race 내부의 모든 텍스트에 적용됩니다.
plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

# 2. 데이터 읽기 및 전처리
data_list = []
pattern = re.compile(r"\[(.+?)\]\s\[(.+?)\]\s\[(.+?)\]")

with open('daily-data.txt', 'r', encoding='utf-8') as f:
    for line in f:
        match = pattern.search(line)
        if match:
            name, identifier, date_str = match.groups()
            user = f"{name}({identifier})"
            date = date_str.split(' ')[0]
            data_list.append({'date': date, 'user': user, 'count': 1})

# 데이터프레임 변환 및 피벗팅
df = pd.DataFrame(data_list)
df_grouped = df.groupby(['date', 'user']).size().reset_index(name='count')
df_pivot = df_grouped.pivot(index='date', columns='user', values='count').fillna(0)
df_cumsum = df_pivot.cumsum()

# 3. 동적 그래프 생성 (TypeError 원인인 font_properties 제거)
bcr.bar_chart_race(
    df=df_cumsum,
    filename='activity_race.mp4', # 또는 'activity_race.html'
    title='daily graph',
    n_bars=10,
    period_length=700,
    steps_per_period=10,
    bar_label_size=10,
    tick_label_size=10,
    title_size=15
)

print("작업이 완료되었습니다.")