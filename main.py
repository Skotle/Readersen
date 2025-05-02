import requests
from bs4 import BeautifulSoup
from collections import defaultdict
from datetime import datetime
import time
import openpyxl
from requests.exceptions import SSLError, RequestException

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
                    day.append(tr[i].find('td',{'class' : 'gall_date'}).text)

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
##base_url = "https://gall.dcinside.com/mini/board/lists/?id="+ID+"&page="
session = requests.Session()
# 헤더 (모바일 안드로이드)
mobile_user_agent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
headers = {
    "User-Agent": mobile_user_agent,
    "X-Requested-With": "XMLHttpRequest",
    #"Referer": url
}

baseurl = "https://m.dcinside.com/mini/"+ID+"/"
times = []
count = 1

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
sheet.append(['이름','글','댓글','조회수','추천','리플','총합'])
for seunil in result_sorted:
    sheet.append([seunil.name,seunil.num,seunil.comm,seunil.view,seunil.recom,seunil.reple,seunil.num*0.56+seunil.comm*0.15]) ##가중값 적용, 글당 0.56점, 댓글당 0.15점

now = datetime.now()
formatted = now.strftime('%m%d%H%M')
filename = ID+'_'+str(formatted)+'.xlsx'

savefile.save("result/example.xlsx")

print("실행시간 %.3f초 (%d초 추가)"%(time.time()-run,2*end_page/8))
print("페이지당 평균 : %.3f초(%d페이지)"%(avg_page,end_page-start_page+1))
print("글당 평균 : %.3f초,(%d글)"%(sum(times)/len(times),len(gall_num)))
