import pandas as pd
import re
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
from datetime import datetime

# 한글 폰트 설정
def set_korean_font():
    candidates = [
        "Malgun Gothic", "AppleGothic", "NanumGothic",
        "NanumBarunGothic", "Gulim", "Dotum"
    ]
    available = {f.name for f in fm.fontManager.ttflist}
    for font in candidates:
        if font in available:
            plt.rcParams['font.family'] = font
            break
    plt.rcParams['axes.unicode_minus'] = False

set_korean_font()

# === 가중치 설정 ===
POST_WEIGHT    = 0.56
COMMENT_WEIGHT = 0.18

def build_mapping(files, pattern):
    id_to_nick = {}
    nick_usage = {}
    for file_path in files:
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
        except FileNotFoundError:
            continue
        for line in lines:
            match = pattern.search(line)
            if match:
                nick, uid, _ = match.groups()
                if uid not in id_to_nick:
                    id_to_nick[uid] = nick
                    nick_usage[nick] = nick_usage.get(nick, 0) + 1

    final_mapping  = {}
    current_counts = {}
    for uid, nick in id_to_nick.items():
        if nick_usage.get(nick, 0) > 1:
            current_counts[nick] = current_counts.get(nick, 0) + 1
            final_mapping[uid] = f"{nick}({current_counts[nick]})"
        else:
            final_mapping[uid] = nick
    return final_mapping

def load_file(file_path, weight, pattern, final_mapping, blacklisted_periods, now, mode):
    raw_data = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except FileNotFoundError:
        print(f"{file_path} 없음, 스킵")
        return raw_data

    for line in lines:
        match = pattern.search(line)
        if match:
            _, uid, date_str = match.groups()
            try:
                dt = datetime.strptime(date_str.strip(), "%Y-%m-%d %H:%M:%S")
                p_month = dt.strftime("%Y-%m")
                if dt > now or p_month in blacklisted_periods or dt.year < 2024:
                    continue
                if mode == 'D':   p = dt.strftime("%Y-%m-%d")
                elif mode == 'W': p = (dt.date() - pd.Timedelta(days=dt.weekday())).strftime("%Y-%m-%d")
                else:             p = p_month
                raw_data.append({
                    'Period': p,
                    'User':   final_mapping.get(uid, "Unknown"),
                    'Weight': weight
                })
            except ValueError:
                continue
    return raw_data

def save_final_clean_report(post_file, comment_file, mode='D'):
    pattern = re.compile(r"\[(.+?)\]\s\[(.+?)\]\s\[(.+?)\]")
    now = datetime.now()
    blacklisted_periods = ['2023-09']

    # 1. 두 파일 통합 매핑
    final_mapping = build_mapping([post_file, comment_file], pattern)

    # 2. 각 파일 로드 (가중치 적용)
    raw_data = (
        load_file(post_file,    POST_WEIGHT,    pattern, final_mapping, blacklisted_periods, now, mode) +
        load_file(comment_file, COMMENT_WEIGHT, pattern, final_mapping, blacklisted_periods, now, mode)
    )

    if not raw_data:
        print("유효한 데이터가 없습니다.")
        return

    df = pd.DataFrame(raw_data)

    # 3. 가중치 합산 피벗 → 누적
    pivot_df          = df.groupby(['Period', 'User'])['Weight'].sum().unstack(fill_value=0).sort_index()
    cumulative_scores = pivot_df.cumsum()

    # 4. 최종 누적 기준 Top 10
    top_users = cumulative_scores.iloc[-1].nlargest(10).index.tolist()
    plot_data = cumulative_scores[top_users]

    # 5. xlsx 저장
    output_file = f'final_scores_{mode}.xlsx'
    plot_data.to_excel(output_file)
    print(f"파일 저장: {output_file}")

    # ============================================================
    # 6. 누적 수치 그래프
    # ============================================================
    periods   = plot_data.index.tolist()
    n_periods = len(periods)

    if n_periods <= 12:   step = 1
    elif n_periods <= 36: step = 2
    else:                 step = max(1, n_periods // 18)

    cmap   = plt.get_cmap('tab20')
    colors = [cmap(i % 20) for i in range(len(top_users))]

    fig, ax = plt.subplots(figsize=(max(14, n_periods * 0.6), 7))

    for i, user in enumerate(top_users):
        scores = plot_data[user].tolist()
        ax.plot(range(n_periods), scores, marker='o', markersize=4,
                linewidth=1.5, label=user, color=colors[i])
        ax.text(n_periods - 1 + 0.1, scores[-1],
                f"{user} ({scores[-1]:.1f})",
                fontsize=8, va='center', color=colors[i])

    tick_positions = list(range(0, n_periods, step))
    ax.set_xticks(tick_positions)
    ax.set_xticklabels([periods[i] for i in tick_positions], rotation=45, ha='right', fontsize=8)

    mode_label = {'D': '일간', 'W': '주간', 'M': '월간'}.get(mode, mode)
    ax.set_ylabel(f"누적 점수 (글×{POST_WEIGHT} + 댓글×{COMMENT_WEIGHT})")
    ax.set_title(f"누적 활동 수치 Top 10 ({mode_label})", fontsize=13)
    ax.legend(loc='upper left', fontsize=7, ncol=2, framealpha=0.5)
    ax.grid(axis='y', linestyle='--', alpha=0.4)

    plt.tight_layout()
    chart_file = f'score_chart_{mode}.png'
    plt.savefig(chart_file, dpi=150)
    plt.show()
    print(f"차트 저장: {chart_file}")

# 실행
save_final_clean_report('daily-data.txt', 'daily-data-comment.txt', mode='W')