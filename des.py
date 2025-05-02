import requests
from bs4 import BeautifulSoup
import time
import openpyxl

class CustomClass:
    def __init__(self, name):
        self.name = name
        self.num = 1
        self.view = 0
        self.recom = 0
        self.reple = 0
        self.comm = 1

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
            for custom_class in self.classes:
                if custom_class.name == name:
                    custom_class.add_member(v, r, rp)
                    break
            else:
                new_class = CustomClass(name)
                new_class.view = v
                new_class.recom = r
                new_class.reple = rp
                self.classes.append(new_class)

    def apply_comm_list(self, comm_names):
        for name in comm_names:
            for custom_class in self.classes:
                if custom_class.name == name:
                    custom_class.comm += 1
                    break
            else:
                new_class = CustomClass(name)
                new_class.num = 0
                new_class.comm = 1
                self.classes.append(new_class)

    def get_classes_sorted_by_num(self):
        return sorted(self.classes, key=lambda x: x.num, reverse=True)

    def print_summary(self):
        for c in self.get_classes_sorted_by_num():
            print(f"{c.name}: num={c.num}, view={c.view}, recom={c.recom}, reple={c.reple}, comm={c.comm}")

# 입력
start_page = int(input('시작 페이지 : '))
end_page = int(input('종료 페이지 : '))
page = start_page

ID = 'bornin04'
baseurl = "https://gall.dcinside.com/mini"
middleurl = "/board/lists/?id=" + ID
headers = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.79 Safari/537.36"
}

analyzer = CustomAnalyzer()
total = [0, 0, 0, 0]
gall_num = []
day = []
times = []

run = time.time()

while True:
    start = time.time()
    try:
        url = f"{baseurl}{middleurl}&page={page}"
        html = BeautifulSoup(requests.get(url, headers=headers).content, 'lxml')
        tr_list = html.select('tbody tr')
    except:
        break  # 파싱 자체가 안 되면 중단

    name, view, recoms, com = [], [], [], []

    for tr in tr_list:
        subject = tr.find('td', class_='gall_subject')
        if not subject or subject.text in ['공지', '고정', '설문']:
            continue

        writer_td = tr.find('td', class_='gall_writer ub-writer')
        count_td = tr.find('td', class_='gall_count')
        recom_td = tr.find('td', class_='gall_recommend')
        reply_span = tr.find('span', class_='reply_num')
        gall_id_td = tr.find('td', class_='gall_num')

        if not (writer_td and count_td and recom_td and gall_id_td):
            continue

        nk = writer_td.text[1:-1]
        co = int(count_td.text)
        ro = int(recom_td.text)
        rp = int(reply_span.text[1]) if reply_span else 0

        if nk[:2] != 'ㅇㅇ' and nk != '익명의팔붕이':
            name.append(nk)
            view.append(co)
            recoms.append(ro)
            com.append(rp)
            if rp > 0:
                gall_num.append(gall_id_td.text)
            day.append(tr.find('td', class_='gall_date').text)


        total[0] += 1
        total[1] += co
        total[2] += ro
        total[3] += rp

    analyzer.analyze_data(name, view, recoms, com)
    times.append(time.time() - start)

    if page % 4 == 0:
        print("\n진행률: %.2f%%" % ((page - start_page + 1) / (end_page - start_page + 1) * 100))

    if page >= end_page:
        break
    page += 1

avg_page = sum(times) / len(times)

# 댓글 파싱
session = requests.Session()
mobile_headers = {
    "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    "X-Requested-With": "XMLHttpRequest"
}
mobile_baseurl = f"https://m.dcinside.com/mini/{ID}/"
times = []
count = 0
for gall_id in gall_num:
    start = time.time()
    try:
        url = mobile_baseurl + str(gall_id)
        res = session.get(url, headers=mobile_headers)
        if res.status_code != 200:
            continue
        html = BeautifulSoup(res.content, 'lxml')
        cmtbox = html.find_all('a', class_='nick')
        analyzer.apply_comm_list([i.text for i in cmtbox])
        count += 1
    except:
        continue
    if count % 20 == 0:
        print(f"댓글 처리 중: {count}개 완료")
    times.append(time.time()-start)

# 결과 출력 및 저장
analyzer.print_summary()
result_sorted = analyzer.get_classes_sorted_by_num()

savefile = openpyxl.Workbook()
sheet = savefile.active
sheet.append(['이름', '글', '댓글', '조회수', '추천', '리플', '총합'])
for user in result_sorted:
    sheet.append([user.name, user.num, user.comm, user.view, user.recom, user.reple, user.num + user.comm])

filename = ID + '_' + str(round(time.time())) + '.xlsx'
savefile.save(filename)

# 요약 출력
print("\n실행시간: %.3f초" % (time.time() - run))
print("페이지당 평균: %.3f초 (%d페이지)" % (avg_page, end_page - start_page + 1))
print("글당 평균 : %.3f초,(%d글)" % (sum(times)/len(times), len(gall_num)))

f = 0
j = 0
for i in result_sorted:
    j+=i.num
    f+=i.comm
print(j,f)