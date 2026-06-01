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

def safe_input(prompt, default):
    try:
        value = input(prompt).strip()
    except EOFError:
        return default
    return value or default

def ask_options():
    mode = safe_input("분석 단위 선택 (D: 일간, W: 주간, M: 월간) [D]: ", "D").upper()
    if mode not in ['D', 'W', 'M']:
        print("잘못된 분석 단위입니다. D로 실행합니다.")
        mode = 'D'

    graph_type = safe_input("그래프 종류 선택 (C: 누적, A: 기간별 집계, B: 둘 다) [B]: ", "B").upper()
    if graph_type not in ['C', 'A', 'B']:
        print("잘못된 그래프 종류입니다. B로 실행합니다.")
        graph_type = 'B'

    top_n = parse_top_n(safe_input("표시할 사용자 수 N [10]: ", "10"))

    bar_style = safe_input("집계 막대 스타일 선택 (S: 쌓기, G: N개 분리) [G]: ", "G").upper()
    if bar_style not in ['S', 'G']:
        print("잘못된 막대 스타일입니다. G로 실행합니다.")
        bar_style = 'G'

    return mode, graph_type, top_n, bar_style

def parse_top_n(value):
    try:
        top_n = int(value)
        return max(1, top_n)
    except ValueError:
        print("잘못된 N 값입니다. 10으로 실행합니다.")
        return 10

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

def save_final_clean_report(post_file, comment_file, mode='D', graph_type='B', top_n=10, bar_style='G'):
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

    # 3. 가중치 합산 피벗 및 누적
    pivot_df          = df.groupby(['Period', 'User'])['Weight'].sum().unstack(fill_value=0).sort_index()
    cumulative_scores = pivot_df.cumsum()

    # 4. 기간 중 한 번이라도 Top N에 들어온 사용자를 모두 표시
    selected_users = select_ever_top_users(pivot_df, cumulative_scores, top_n)
    cumulative_plot_data = cumulative_scores[selected_users]
    aggregate_plot_data = build_period_top_n_data(pivot_df, selected_users, top_n)

    # 5. xlsx 저장
    output_file = f'final_scores_{mode}.xlsx'
    selected_user_summary = pd.DataFrame({
        'User': selected_users,
        'FinalCumulativeScore': [cumulative_scores.iloc[-1].get(user, 0) for user in selected_users]
    })
    with pd.ExcelWriter(output_file, engine='openpyxl') as writer:
        selected_user_summary.to_excel(writer, sheet_name='selected_users', index=False)
        cumulative_plot_data.to_excel(writer, sheet_name='cumulative_selected')
        aggregate_plot_data.to_excel(writer, sheet_name='period_top_n_only')
        cumulative_scores.to_excel(writer, sheet_name='cumulative_all')
        pivot_df.to_excel(writer, sheet_name='period_aggregate_all')
    print(f"파일 저장: {output_file}")

    if graph_type in ['C', 'B']:
        save_cumulative_chart(cumulative_plot_data, selected_users, mode, top_n)
    if graph_type in ['A', 'B']:
        save_aggregate_chart(aggregate_plot_data, selected_users, mode, bar_style, top_n)

def select_ever_top_users(pivot_df, cumulative_scores, top_n):
    selected = set()
    for _, row in pivot_df.iterrows():
        top_users = row[row > 0].nlargest(top_n).index.tolist()
        selected.update(top_users)

    if not selected:
        selected = set(cumulative_scores.iloc[-1].nlargest(top_n).index.tolist())

    final_scores = cumulative_scores.iloc[-1]
    return sorted(selected, key=lambda user: final_scores.get(user, 0), reverse=True)

def build_period_top_n_data(pivot_df, selected_users, top_n):
    result = pd.DataFrame(0.0, index=pivot_df.index, columns=selected_users)
    for period, row in pivot_df.iterrows():
        top_series = row[row > 0].nlargest(top_n)
        for user, value in top_series.items():
            if user in result.columns:
                result.at[period, user] = value
    return result

def build_user_colors(users):
    cmap = plt.get_cmap('hsv', max(1, len(users) + 1))
    return {user: cmap(i) for i, user in enumerate(users)}

def calculate_tick_step(n_periods):
    if n_periods <= 12:
        return 1
    if n_periods <= 36:
        return 2
    return max(1, n_periods // 18)

def save_cumulative_chart(plot_data, top_users, mode, top_n):
    periods   = plot_data.index.tolist()
    n_periods = len(periods)
    step = calculate_tick_step(n_periods)

    colors = build_user_colors(top_users)

    fig, ax = plt.subplots(figsize=(max(14, n_periods * 0.6), 7))

    for i, user in enumerate(top_users):
        scores = plot_data[user].tolist()
        ax.plot(range(n_periods), scores, marker='o', markersize=4,
                linewidth=1.5, label=user, color=colors[user])
        ax.text(n_periods - 1 + 0.1, scores[-1],
                f"{user} ({scores[-1]:.1f})",
                fontsize=8, va='center', color=colors[user])

    tick_positions = list(range(0, n_periods, step))
    ax.set_xticks(tick_positions)
    ax.set_xticklabels([periods[i] for i in tick_positions], rotation=45, ha='right', fontsize=8)

    mode_label = {'D': '일간', 'W': '주간', 'M': '월간'}.get(mode, mode)
    ax.set_ylabel(f"누적 점수 (글×{POST_WEIGHT} + 댓글×{COMMENT_WEIGHT})")
    ax.set_title(f"누적 활동 수치 - 기간 중 Top {top_n} 진입자 {len(top_users)}명 ({mode_label})", fontsize=13)
    ax.legend(loc='upper left', fontsize=7, ncol=2, framealpha=0.5)
    ax.grid(axis='y', linestyle='--', alpha=0.4)

    plt.tight_layout()
    chart_file = f'score_chart_{mode}.png'
    plt.savefig(chart_file, dpi=150)
    plt.show()
    print(f"차트 저장: {chart_file}")

def save_aggregate_chart(plot_data, top_users, mode, bar_style='G', top_n=10):
    if bar_style == 'S':
        save_stacked_aggregate_chart(plot_data, top_users, mode, top_n)
    else:
        save_grouped_aggregate_chart(plot_data, top_users, mode, top_n)

def save_stacked_aggregate_chart(plot_data, top_users, mode, top_n):
    periods = plot_data.index.tolist()
    n_periods = len(periods)
    step = calculate_tick_step(n_periods)
    x = range(n_periods)

    fig, ax = plt.subplots(figsize=(max(14, n_periods * 0.6), 7))
    bottom = [0] * n_periods
    colors = build_user_colors(top_users)

    for i, user in enumerate(top_users):
        values = plot_data[user].tolist()
        ax.bar(x, values, bottom=bottom, label=user, color=colors[user], width=0.75)
        bottom = [b + v for b, v in zip(bottom, values)]

    tick_positions = list(range(0, n_periods, step))
    ax.set_xticks(tick_positions)
    ax.set_xticklabels([periods[i] for i in tick_positions], rotation=45, ha='right', fontsize=8)

    mode_label = {'D': '일간', 'W': '주간', 'M': '월간'}.get(mode, mode)
    ax.set_ylabel(f"기간별 점수 (글×{POST_WEIGHT} + 댓글×{COMMENT_WEIGHT})")
    ax.set_title(f"{mode_label} 집계 활동 수치 - 기간 중 Top {top_n} 진입자 {len(top_users)}명", fontsize=13)
    ax.legend(loc='upper left', fontsize=7, ncol=2, framealpha=0.5)
    ax.grid(axis='y', linestyle='--', alpha=0.4)

    plt.tight_layout()
    chart_file = f'score_chart_aggregate_{mode}.png'
    plt.savefig(chart_file, dpi=150)
    plt.show()
    print(f"집계 차트 저장: {chart_file}")

def save_grouped_aggregate_chart(plot_data, top_users, mode, top_n):
    periods = plot_data.index.tolist()
    n_periods = len(periods)
    step = calculate_tick_step(n_periods)
    group_width = 0.82
    bar_width = min(0.22, group_width / max(1, top_n))
    x = list(range(n_periods))
    colors = build_user_colors(top_users)

    fig_width = max(14, n_periods * max(0.75, top_n * 0.13))
    fig, ax = plt.subplots(figsize=(fig_width, 7))
    labeled_users = set()

    for period_index, period in enumerate(periods):
        ranked = plot_data.loc[period]
        ranked = ranked[ranked > 0].sort_values(ascending=False).head(top_n)
        count = len(ranked)
        if count == 0:
            continue

        start_offset = -((count - 1) * bar_width) / 2
        for rank_index, (user, value) in enumerate(ranked.items()):
            offset = x[period_index] + start_offset + rank_index * bar_width
            label = user if user not in labeled_users else None
            ax.bar(offset, value, width=bar_width, label=label, color=colors[user])
            labeled_users.add(user)

    tick_positions = list(range(0, n_periods, step))
    ax.set_xticks(tick_positions)
    ax.set_xticklabels([periods[i] for i in tick_positions], rotation=45, ha='right', fontsize=8)

    mode_label = {'D': '일간', 'W': '주간', 'M': '월간'}.get(mode, mode)
    ax.set_ylabel(f"기간별 점수 (글×{POST_WEIGHT} + 댓글×{COMMENT_WEIGHT})")
    ax.set_title(f"{mode_label} 집계 분리 막대 - 각 기간 Top {top_n}만 표시", fontsize=13)
    ax.legend(loc='upper left', fontsize=7, ncol=2, framealpha=0.5)
    ax.grid(axis='y', linestyle='--', alpha=0.4)

    plt.tight_layout()
    chart_file = f'score_chart_aggregate_grouped_{mode}.png'
    plt.savefig(chart_file, dpi=150)
    plt.show()
    print(f"분리 집계 차트 저장: {chart_file}")

# 실행
selected_mode, selected_graph_type, selected_top_n, selected_bar_style = ask_options()
save_final_clean_report(
    'daily-data.txt',
    'daily-data-comment.txt',
    mode=selected_mode,
    graph_type=selected_graph_type,
    top_n=selected_top_n,
    bar_style=selected_bar_style
)
