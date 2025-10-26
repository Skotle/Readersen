import pandas as pd
import re
file_path = "example.txt"

<<<<<<< HEAD
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.read().splitlines()

header = lines[0].split(',')  # CSV 헤더
header.append("가중값")        # 계산 열 추가

file = "ip-black.txt"
with open(file, "r", encoding="utf-8") as f:
    readip = f.read().splitlines()

filter_values = [re.sub(r"\[.*?\]", "", line).strip() for line in readip if line.strip()]

temp_rows = []  # 첫 반복에서 계산 후 저장
total_temp = [0, 0, 0, 0, 0, 0]

# 1차 반복: 모든 행 계산 및 가중값 추가
for line in lines[1:]:
    parts = line.split('SPLIT')
    
    # 이름 수정
    if '.' in parts[1] or parts[0] == 'ㅇㅇ':
        parts[0] = f"{parts[0]}({parts[1]})"
    
    # 숫자 변환 (글, 댓글, 조회, 추천, 리플)
    for i in range(2, 7):
        parts[i] = int(parts[i]) if parts[i].isdigit() else 0
    
    # 가중값 계산
    calc = parts[2]*0.56 + parts[6]*0.18
    parts.append(round(calc, 1))
    
    temp_rows.append(parts)
    total_temp[0] += parts[2]
    total_temp[1] += parts[3]
    total_temp[2] += parts[4]
    total_temp[3] += parts[5]
    total_temp[4] += parts[6]
    total_temp[5] += parts[7]

# 2차 반복: 필터링 후 최종 rows에 저장
rows = []

for parts in temp_rows:
    if parts[1] in filter_values:
        continue
    rows.append(parts)

# DataFrame 생성 및 엑셀 저장
df = pd.DataFrame(rows, columns=header)
df.to_excel("user-data.xlsx", index=False, engine='openpyxl')
print("user-data.xlsx 파일이 생성되었습니다.")
print("최종 총합:", total_temp)
=======
file_path = "example.txt"

with open(file_path, "r", encoding="utf-8") as f:
    lines = f.read().splitlines()

header = lines[0].split(',')  # CSV 헤더
header.append("가중값")        # 계산 열 추가

rows = []
for line in lines[1:]:
    parts = line.split('SPLIT')  # CustomAnalyzer 출력 기준
    for i in parts[1]:
        if i=='.':
            parts[0] = parts[0] +'('+parts[1]+')'
            break
        elif parts[0]=='ㅇㅇ':
            parts[0] = parts[0] +'('+parts[1]+')'
            break
    # 숫자 부분만 정수 변환 (글, 조회, 추천, 리플, 댓글)
    for i in range(2, 7):
        parts[i] = int(parts[i]) if parts[i].isdigit() else 0
    # 계산: 글*0.56 + 댓글*0.18
    calc = parts[2] * 0.56 + parts[6] * 0.18
    parts.append(calc)
    rows.append(parts)

df = pd.DataFrame(rows, columns=header)
df.to_excel("user-data.xlsx", index=False, engine='openpyxl')
print("user-data.xlsx 파일이 생성되었습니다.")
>>>>>>> 7c27da370a6e63cd23ede2070ed9feb266056010
