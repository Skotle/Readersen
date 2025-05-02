import requests
from requests.exceptions import SSLError, RequestException

url = "https://fdd"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
}

try:
    response = requests.get(url, headers=headers, timeout=10)
    response.raise_for_status()  # HTTP 오류 상태 코드가 있을 경우 예외 발생
    print("요청 성공!")
    print(response.text)

except SSLError as ssl_err:
    print("❌ SSL 오류 발생:")
    print(ssl_err)

except RequestException as req_err:
    print("❌ 요청 중 오류 발생:")
    print(req_err)

except Exception as e:
    print("❌ 알 수 없는 오류 발생:")
    print(e)
