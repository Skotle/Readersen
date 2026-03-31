import pandas as pd
import json
import re

# 1) 설정 값
json_file_path = "result.json"
black_list_path = "ip-black.txt"
output_excel_path = "result_output.xlsx"

# 2) ip-black.txt 필터 목록 생성
try:
    with open(black_list_path, "r", encoding="utf-8") as f:
        readip = f.read().splitlines()
    # [xxx] 제거 후 필터 값만 추출
    filter_values = [re.sub(r"\[.*?\]", "", line).strip() for line in readip if line.strip()]
except FileNotFoundError:
    filter_values = []
    print(f"공지: {black_list_path} 파일이 없어 필터를 적용하지 않습니다.")

# 3) result.json 읽기
with open(json_file_path, "r", encoding="utf-8") as f:
    data = json.load(f)

# 4) 데이터 가공 및 필터링
rows = []
for item in data:
    name = item.get("name", "")
    id_or_ip = item.get("id_or_ip", "")
    
    # 블랙리스트 필터링 (id_or_ip 기준)
    if id_or_ip in filter_values:
        continue

    # 이름 수정 로직: 아이피(.)가 포함되어 있거나 이름이 'ㅇㅇ'인 경우 (이름(아이피) 형식)
    if '.' in id_or_ip or name == 'ㅇㅇ':
        display_name = f"{name}({id_or_ip})"
    else:
        display_name = name

    # 숫자 데이터 추출 (num, view, recom, reple, comm)
    num = item.get("num", 0)
    view = item.get("view", 0)
    recom = item.get("recom", 0)
    reple = item.get("reple", 0)
    comm = item.get("comm", 0)

    # 가중값 계산 (기존 로직 유지 예시)
    weight = 0
    if num > 300:
        # 가중치 계산 공식이 필요하다면 여기에 추가하세요.
        # 예: weight = (num * 2) + reple
        pass
    
    rows.append([
        display_name, id_or_ip, item.get("nicktype", ""), 
        num, view, recom, reple, comm, weight
    ])

# 5) 엑셀 저장
columns = ["이름", "ID/IP", "닉네임타입", "글수", "조회수", "추천수", "리플수", "댓글수", "가중값"]
df = pd.DataFrame(rows, columns=columns)

# 엑셀 파일로 출력
df.to_excel(output_excel_path, index=False)
print(f"성공: {output_excel_path} 파일이 생성되었습니다.")