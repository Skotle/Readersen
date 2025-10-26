import pandas as pd

# 텍스트 파일 직접 처리
with open("user-data.txt", "r", encoding="utf-8") as f:
    lines = f.readlines()

# 각 줄을 SPLIT 기준으로 나눔
data = [line.strip().split("SPLIT") for line in lines]

for f in data:
    for i in range(1,7):
        f[i] = int(f[i])
    f.append(round(float(f[1])*0.56+float(f[5])*0.15,2))

# DataFrame 생성
df = pd.DataFrame(data, columns=["이름", "글", "조회수", "추천", "리플", "댓글","비추천","가중점수"])

# 엑셀 저장
df.to_excel("result/example.xlsx", index=False)

print("엑셀 저장 완료")
