import pandas as pd
import re
from datetime import datetime

def save_final_clean_report(file_path, mode='M'):
    pattern = re.compile(r"\[(.+?)\]\s\[(.+?)\]\s\[(.+?)\]")
    
    # 1. 고유 사용자 식별 및 닉네임 번호 부여
    id_to_nick = {}
    nick_usage = {}
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for line in lines:
        match = pattern.search(line)
        if match:
            nick, uid, _ = match.groups()
            if uid not in id_to_nick:
                id_to_nick[uid] = nick
                nick_usage[nick] = nick_usage.get(nick, 0) + 1

    final_mapping = {}
    current_counts = {}
    for uid, nick in id_to_nick.items():
        if nick_usage.get(nick, 0) > 1:
            current_counts[nick] = current_counts.get(nick, 0) + 1
            final_mapping[uid] = f"{nick}({current_counts[nick]})"
        else:
            final_mapping[uid] = nick

    # 2. 유효 데이터 필터링 (이상값 집중 제거)
    raw_data = []
    now = datetime.now()
    
    # 제외할 특정 이상값 시점 (예: 2023-09)
    blacklisted_periods = ['2023-09'] 

    for line in lines:
        match = pattern.search(line)
        if match:
            _, uid, date_str = match.groups()
            try:
                dt = datetime.strptime(date_str.strip(), "%Y-%m-%d %H:%M:%S")
                
                # 기간 문자열 생성
                p_month = dt.strftime("%Y-%m")
                
                # 필터링 조건: 
                # 1. 미래 날짜 제외
                # 2. 블랙리스트 기간(2023-09 등) 제외
                # 3. 너무 과거 데이터(분석 시작점 이전) 제외
                if dt > now or p_month in blacklisted_periods or dt.year < 2024:
                    continue
                
                if mode == 'D': p = dt.strftime("%Y-%m-%d")
                elif mode == 'W': p = (dt.date() - pd.Timedelta(days=dt.weekday())).strftime("%Y-%m-%d")
                else: p = p_month
                
                raw_data.append({'Period': p, 'User': final_mapping.get(uid, "Unknown")})
            except ValueError:
                continue

    if not raw_data:
        print("유효한 데이터가 없습니다. 필터링 조건을 확인하세요.")
        return

    df = pd.DataFrame(raw_data)
    
    # 3. 누계 및 순위 계산
    pivot_df = df.groupby(['Period', 'User']).size().unstack(fill_value=0)
    cumulative_counts = pivot_df.sort_index().cumsum()
    
    # 순위 배정 (동점자 발생 시 먼저 나온 사람 우선하여 순위 뭉침 방지)
    cumulative_ranks = cumulative_counts.rank(axis=1, method='first', ascending=False)

    # 4. 역대 Top 10 유저 필터링
    ever_top_10 = set()
    for p in cumulative_ranks.index:
        top_10 = cumulative_ranks.loc[p][cumulative_ranks.loc[p] <= 10].index.tolist()
        ever_top_10.update(top_10)
    
    target_users = sorted(list(ever_top_10))
    final_ranks = cumulative_ranks[target_users]

    # 5. 저장
    output_file = f'final_ranking_no_outliers_{mode}.xlsx'
    final_ranks.to_excel(output_file)
    
    print(f"--- 이상값 제거 완료 ---")
    print(f"제외된 기간: {blacklisted_periods}")
    print(f"분석 시작: {final_ranks.index[0]} ~ 종료: {final_ranks.index[-1]}")
    print(f"파일 저장: {output_file}")

# 실행
save_final_clean_report('daily-data.txt', mode='M')