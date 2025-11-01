import pandas as pd
import re

file_path = "example.txt"

# 1) example.txt 읽기
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.read().splitlines()

header = lines[0].split(',')  # 첫 줄은 헤더
header.append("가중값")        # 가중값 열 추가

# 2) ip-black.txt 필터 목록 생성
file = "ip-black.txt"
with open(file, "r", encoding="utf-8") as f:
    readip = f.read().splitlines()

# [xxx] 제거 후 필터 값만 추출
filter_values = [re.sub(r"\[.*?\]", "", line).strip() for line in readip if line.strip()]
rows = []
for line in lines[1:]:
    parts = line.split('SPLIT')
    
    # 이름 수정: parts[1]에 '.'이 있거나, parts[0] == 'ㅇㅇ'이면 이름 추가
    if '.' in parts[1] or parts[0] == 'ㅇㅇ':
        parts[0] = f"{parts[0]}({parts[1]})"

    # 필터(ip-black) 제외 처리

    # 숫자 변환 (글, 댓글, 조회, 추천, 리플)
    for i in range(2, 7):
        parts[i] = int(parts[i]) if parts[i].isdigit() else 0

    # 가중값 계산
    if parts[2]>300:
        print("글 %d개, 댓글 %d개"%(parts[2],parts[6]))
    calc = round((parts[2]*0.56+parts[6]*0.18), 2)
        #calc = round((parts[2]/3+parts[6]/3.9+parts[3]/2000+parts[4]/70+parts[5]/100), 2)
    parts.append(calc)

    rows.append(parts)

# for i in rows[:]: 
#     if i[1] in filter_values:
#         rows.remove(i)


# DataFrame 생성
df = pd.DataFrame(rows, columns=header)

# 3) 저장 (xlsx + csv 둘 다)
df.to_excel("user-data.xlsx", index=False, engine='openpyxl')
df.to_csv("user-data.csv", index=False, encoding="utf-8-sig")

print("user-data.xlsx 및 user-data.csv 파일이 생성되었습니다.")
