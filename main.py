import requests
from bs4 import BeautifulSoup
from collections import defaultdict
from collections import Counter
from datetime import datetime
import time
import openpyxl
import matplotlib.pyplot as plt
from requests.exceptions import SSLError, RequestException
import numpy as np

class CustomClass:
    def __init__(self, name):
        self.name = name
        self.num = 1
        self.view = 0
        self.recom = 0
        self.reple = 0
        self.comm = 1  # 커뮤니케이션 수치 (예: 언급 횟수)

    def add_member(self, view, recom, reple):
        self.num += 1
        self.view += view
        self.recom += recom
        self.reple += reple

class CustomAnalyzer:
    def __init__(self):
        self.classes = []

    def analyze_data(self, names, views, recoms, reples):
        for name, v, r, rp in zip(names, views, recoms, reples):
            found = False
            for custom_class in self.classes:
                if custom_class.name == name:
                    custom_class.add_member(v, r, rp)
                    found = True
                    break
            if not found:
                new_class = CustomClass(name)
                new_class.view = v
                new_class.recom = r
                new_class.reple = rp
                self.classes.append(new_class)

    def apply_comm_list(self, comm_names):
        for name in comm_names:
            found = False
            for custom_class in self.classes:
                if custom_class.name == name:
                    custom_class.comm += 1
                    found = True
                    break
            if not found:
                new_class = CustomClass(name)
                new_class.num = 0
                new_class.comm = 1
                self.classes.append(new_class)

    def get_classes_sorted_by_num(self):
        return sorted(self.classes, key=lambda x: x.num, reverse=True)

    def print_summary(self):
        for c in self.get_classes_sorted_by_num():
            print(f"{c.name}: num={c.num}, view={c.view}, recom={c.recom}, reple={c.reple}, comm={c.comm}")



start_page = int(input('시작 페이지 : '))
page = start_page
end_page = int(input('종료 페이지 : '))



baseurl = "https://gall.dcinside.com/mini"
ID = 'bornin10'
middleurl = "/board/lists/?id="+ID

headers = {"User-Agent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.79 Safari/537.36"}

analyzer = CustomAnalyzer()

total = [0,0,0,0]

day = []

gall_num = []

run = time.time()
times = []
while True:
    start = time.time()
    key = baseurl+middleurl+"&page="+str(page)
    try:
        response =requests.get(key,headers=headers)
        html = BeautifulSoup(response.content,'html.parser')

        tr = []
        tr = html.find('tbody').find_all('tr')

        name = []
        view = []
        recoms = []
        com = []
        for i in range(len(tr)):
            if tr[i].find('td',{'class':'gall_subject'}).text not in ['공지','고정','설문']:
                nk = tr[i].find('td',{'class' : 'gall_writer ub-writer'}).text[1:-1]
                co = int(tr[i].find('td',{'class' : 'gall_count'}).text)
                ro = int(tr[i].find('td',{'class':'gall_recommend'}).text)
                rp = tr[i].find('span',{'class':'reply_num'})
                day.append(tr[i].find('td',{'class' : 'gall_date'}).text)
                total[0]+=1
                total[1]+=co
                total[2]+=ro
                
                if rp != None:
                    rp=int(rp.text[1])
                    gall_num.append(tr[i].find('td',{'class':'gall_num'}).text)
                    total[3]+=rp
                else:
                    rp=0

                if nk[:2] not in ['ㅇㅇ'] and nk!='익명의팔붕이':
                    name.append(nk)
                    view.append(co)
                    recoms.append(ro)
                    com.append(rp)

        analyzer.analyze_data(name,view,recoms,com)
    except SSLError:
        print("SSL 예외 발생, 건너뜀\n")
        continue
    except RequestException:
        print("요청 오류 발생, 건너뜀\n")
        continue
    except Exception:
        print("예외 발생, 건너뜀\n")
        continue
    times.append(time.time()-start)
    if page%8==0:
        print("\n\n페이지 파싱  중 : %.2f%s\n"%((page-start_page+1)/(end_page-start_page+1)*100,'%'))
        time.sleep(2)
    if page >= end_page:
        break
    #time.sleep(delay)
    page +=1
avg_page = sum(times)/len(times)

#3ID = "bornin10"
session = requests.Session()
# 헤더 (모바일 안드로이드)
mobile_user_agent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
headers = {
    "User-Agent": mobile_user_agent,
    "X-Requested-With": "XMLHttpRequest",
    #"Referer": url
}

baseurl = "https://m.dcinside.com/mini/"+ID+"/" ##미니갤일때
#baseurl = "https://m.dcinside.com/board/"+ID+"/" ##일반갤일때

times = []
count = 1

comment_count = 0
for i in gall_num:
    start = time.time()
    key = baseurl+str(i)
    #os.system('cls' if os.name == 'nt' else 'clear')
    if int(i)%4==0:
        print("\r글 파싱중 : %s"%(str(round(count/len(gall_num)*100,2))+'%'))
    try:
        response = requests.get(key,headers=headers,timeout=15)
        html = BeautifulSoup(response.content,'lxml')
        cmtbox = html.find_all('a',{'class':'nick'})
        comment_count+=len(cmtbox)
        NICK_comments = []
        for i in cmtbox:
            NICK_comments.append(i.text)
        analyzer.apply_comm_list(NICK_comments)
    except SSLError:
        print("SSL 예외 발생, 건너뜀\n")
        continue
    except RequestException:
        print("요청 오류 발생, 건너뜀\n")
        continue
    except Exception:
        print("예외 발생, 건너뜀\n")
        continue
    #print(NICK_comments)
    times.append(time.time()-start)
    count+=1
    #time.sleep(2)

analyzer.print_summary()
result_sorted = analyzer.get_classes_sorted_by_num()

savefile = openpyxl.Workbook()
sheet = savefile.active
sheet.append(['이름','글','댓글','조회수','추천','리플','가중점수'])
sheet.append(['총계',total[0],comment_count,total[1],total[2],total[3],'-'])
for seunil in result_sorted:
    sheet.append([seunil.name,seunil.num,seunil.comm,seunil.view,seunil.recom,seunil.reple,seunil.num*0.56+seunil.comm*0.15]) ##가중값 적용, 글당 0.56점, 댓글당 0.15점

now = datetime.now()
formatted = now.strftime('%m%d%H%M')
filename = ID+'_'+str(formatted)+'.xlsx'

savefile.save("result/"+filename)

print("실행시간 %.3f초 (%d초 추가)"%(time.time()-run,2*end_page/8))
print("페이지당 평균 : %.3f초(%d페이지)"%(avg_page,end_page-start_page+1))
print("글당 평균 : %.3f초,(%d글)"%(sum(times)/len(times),len(gall_num)))

today_str = datetime.now().strftime("%m.%d")  # 오늘 날짜

# 시간(HH:MM)은 오늘 날짜로 간주
processed_days = []
for d in day:
    if ":" in d:
        processed_days.append(today_str)
    else:
        processed_days.append(d)

# 날짜별 카운트
counter = Counter(processed_days)

# 1차 필터링: 50개 이상
counter = {date: count for date, count in counter.items() if count >= 50}

# 평균 계산
if counter:
    counts = list(counter.values())
    average = sum(counts) / len(counts)

    # 2차 필터링: 평균의 60% 이상
    filtered_counter = {
        date: count for date, count in counter.items()
        if count >= average * 0.6
    }

    if filtered_counter:
        # 정렬
        dates = sorted(filtered_counter)
        counts = [filtered_counter[d] for d in dates]
        cumulative_counts = np.cumsum(counts)

        # --- 첫 번째: 막대 그래프 ---
        plt.figure(figsize=(10, 4))
        bars = plt.bar(dates, counts, color='skyblue', label='Count')
        plt.axhline(y=average, color='red', linestyle='--', label=f'Average ({average:.1f})')

        # 막대 위에 수치 표시
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width() / 2, height + 1, f'{int(height)}',
                     ha='center', va='bottom', fontsize=9)

        plt.xlabel("Date (MM.DD)")
        plt.ylabel("Count")
        plt.title("Activity Count per Day (Filtered)")
        plt.xticks(rotation=45)
        plt.legend()
        plt.tight_layout()
        plt.show()

        # --- 두 번째: 누적 그래프 ---
        plt.figure(figsize=(10, 4))
        plt.plot(dates, cumulative_counts, marker='o', color='green', label='Cumulative Count')

        # 누적 수치 표시
        for x, y in zip(dates, cumulative_counts):
            plt.text(x, y + 2, f'{int(y)}', ha='center', va='bottom', fontsize=9)

        plt.xlabel("Date (MM.DD)")
        plt.ylabel("Cumulative Count")
        plt.title("Cumulative Activity Over Time")
        plt.xticks(rotation=45)
        plt.grid(True, linestyle='--', alpha=0.5)
        plt.legend()
        plt.tight_layout()
        plt.show()

    else:
        print("평균의 60% 이상인 날짜가 없습니다. 그래프를 그릴 수 없습니다.")
else:
    print("50개 이상인 날짜가 없습니다. 그래프를 그릴 수 없습니다.")