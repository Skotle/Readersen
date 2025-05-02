from selenium import webdriver
from selenium.webdriver.chrome.options import Options

# 모바일 User-Agent (iPhone 예시)
mobile_user_agent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) " \
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) " \
                    "Version/15.0 Mobile/15E148 Safari/604.1"
# 옵션 설정
options = Options()
options.add_argument(f"user-agent={mobile_user_agent}")

# 크롬 드라이버 실행
driver = webdriver.Chrome(options=options)
key = "https://m.dcinside.com/mini/bornin10/119344"
# 접속할 사이트 (모바일 전용 페이지 확인)
driver.get(key)  # 예: 네이버 모바일 사이트

# 필요한 작업 수행...
