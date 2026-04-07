
## 자바 랭킹 크롤러

## 실행

* 첫 페이지 번호(PC기준), 마지막 페이지 번호 입력
* 페이지당 거의 50글
* main -메이저 m - 마이너 mini - 미니

## 속도(RPS)
* 1스레드 28~
* 2스레드 87~
* 4스레드 135~
* 6스레드 185~
* 8스레드 185+ (리미터 적용)

## 가공

```bash
python ExcelPrinter.py
```
```bash
python GraphPrinter.py
```
* example.json, daily-data.txt에서 랭킹, 일일 데이터 커스텀 가능

## ⚠️ 요구 사항
* JAVA 21 이상


파이썬 실행용
* python 3
* pandas
* matplotlib
* numoy
* collections
---
