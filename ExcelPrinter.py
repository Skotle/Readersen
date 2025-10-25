import pandas as pd

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
