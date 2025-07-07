from locust import HttpUser, task, between, events
import random

class EntryOnlyUser(HttpUser):
    """Entry 경로만 테스트하는 사용자"""
    wait_time = between(0.5, 2.0)
    
    def on_start(self):
        """테스트 시작 시 초기화"""
        self.user_id = random.randint(1000, 99999)
        print(f"🚀 Entry 테스트 사용자 {self.user_id} 시작")
    
    @task(weight=10)
    def entry_form_submit(self):
        """Entry 폼 제출 테스트 (메인 테스트)"""
        form_data = {"userId": self.user_id}
        
        with self.client.post(
            "/entry/join", 
            data=form_data,
            allow_redirects=False,
            catch_response=True
        ) as response:
            if response.status_code in [302, 303]:
                redirect_url = response.headers.get('Location', '')
                
                if '/entry/waiting' in redirect_url:
                    print(f"[{self.user_id}] 대기열 진입 성공")
                    response.success()
                    
                elif '/seat' in redirect_url:
                    print(f"[{self.user_id}] 바로 입장 허용")
                    response.success()
                    
                elif '/entry' in redirect_url:
                    print(f"[{self.user_id}] Entry로 리다이렉트 (오류)")
                    response.success()
                    
                else:
                    print(f"[{self.user_id}] 예상치 못한 리다이렉트: {redirect_url}")
                    response.failure(f"Unexpected redirect: {redirect_url}")
                    
            else:
                print(f"[{self.user_id}] Entry 제출 실패: {response.status_code}")
                response.failure(f"Entry submit failed: {response.status_code}")
    
    @task(weight=3)
    def entry_page_load(self):
        """Entry 페이지 로드 테스트"""
        with self.client.get("/entry", catch_response=True) as response:
            if response.status_code == 200:
                if "바로 입장 가능" in response.text:
                    print(f"✅ [{self.user_id}] Entry 페이지 로드 - 바로 입장 가능")
                elif "대기열 입장" in response.text:
                    print(f"✅ [{self.user_id}] Entry 페이지 로드 - 대기열 모드")
                else:
                    print(f"✅ [{self.user_id}] Entry 페이지 로드 성공")
                response.success()
            else:
                print(f"❌ [{self.user_id}] Entry 페이지 로드 실패: {response.status_code}")
                response.failure(f"Entry page failed: {response.status_code}")


class FastEntryUser(HttpUser):
    """빠른 속도로 Entry 요청하는 사용자"""
    wait_time = between(0.1, 0.5)
    weight = 2
    
    def on_start(self):
        self.user_id = random.randint(100000, 999999)
        print(f"🔥 빠른 Entry 사용자 {self.user_id} 시작")
    
    @task
    def rapid_entry_submit(self):
        """빠른 Entry 폼 제출"""
        form_data = {"userId": self.user_id}
        
        with self.client.post(
            "/entry/join", 
            data=form_data,
            allow_redirects=False,
            catch_response=True
        ) as response:
            if response.status_code in [302, 303]:
                redirect_url = response.headers.get('Location', '')
                if '/entry/waiting' in redirect_url:
                    print(f"🔥 [FAST-{self.user_id}] 대기열")
                elif '/seat' in redirect_url:
                    print(f"🔥 [FAST-{self.user_id}] 바로진입")
                else:
                    print(f"🔥 [FAST-{self.user_id}] 기타: {redirect_url}")
                response.success()
            else:
                print(f"🔥 [FAST-{self.user_id}] 실패: {response.status_code}")
                response.failure(f"Fast entry failed: {response.status_code}")


class SlowEntryUser(HttpUser):
    """느린 속도로 Entry 요청하는 사용자 (실제 사용자와 유사)"""
    wait_time = between(3, 10)
    weight = 1
    
    def on_start(self):
        self.user_id = random.randint(10000, 99999)
        print(f"🐌 느린 Entry 사용자 {self.user_id} 시작")
    
    @task
    def slow_entry_process(self):
        """느린 Entry 프로세스 (페이지 로드 → 대기 → 제출)"""
        
        with self.client.get("/entry", catch_response=True) as response:
            if response.status_code == 200:
                print(f"🐌 [SLOW-{self.user_id}] 페이지 로드")
                response.success()
            else:
                print(f"🐌 [SLOW-{self.user_id}] 페이지 로드 실패")
                response.failure("Slow page load failed")
                return
        
        self.wait_time = between(2, 5)
        
        form_data = {"userId": self.user_id}
        
        with self.client.post(
            "/entry/join", 
            data=form_data,
            allow_redirects=False,
            catch_response=True
        ) as response:
            if response.status_code in [302, 303]:
                redirect_url = response.headers.get('Location', '')
                print(f"🐌 [SLOW-{self.user_id}] 제출 완료: {redirect_url}")
                response.success()
            else:
                print(f"🐌 [SLOW-{self.user_id}] 제출 실패: {response.status_code}")
                response.failure("Slow entry submit failed")


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("🎯 Entry 경로 전용 부하 테스트 시작")
    print("=" * 60)
    print("테스트 대상: /entry, /entry/join")
    print("테스트 시나리오:")
    print("1. EntryOnlyUser (weight=1) - 일반 Entry 테스트")
    print("2. FastEntryUser (weight=2) - 빠른 Entry 테스트") 
    print("3. SlowEntryUser (weight=1) - 느린 Entry 테스트")
    print("=" * 60)

@events.test_stop.add_listener  
def on_test_stop(environment, **kwargs):
    print("=" * 60)
    print("Entry 경로 부하 테스트 완료")
    
    stats = environment.stats
    print(f"총 요청 수: {stats.total.num_requests}")
    print(f"실패 수: {stats.total.num_failures}")
    print(f"평균 응답 시간: {stats.total.avg_response_time:.2f}ms")

# 추가 이벤트: 실시간 모니터링
@events.request.add_listener
def on_request(request_type, name, response_time, response_length, exception, context, **kwargs):
    """요청별 실시간 로깅"""
    if exception:
        print(f"❌ {request_type} {name} - 오류: {exception}")
    elif response_time > 5000:
        print(f"⚠️ {request_type} {name} - 느린 응답: {response_time:.0f}ms")

if __name__ == "__main__":
    print("Entry 경로 전용 Locust 스크립트")
    print("=" * 40)
    print("실행 명령어:")
    print("locust -f entry_only_test.py --host=http://localhost:8080")
    print()
    print("권장 설정:")
    print("- 사용자 수: 20-100명")
    print("- Spawn rate: 2-5 users/sec")
    print("- 테스트 시간: 3-10분")
    print()
    print("모니터링 포인트:")
    print("- 과부하 상태 전환 관찰")
    print("- 리다이렉트 패턴 분석")
    print("- 응답 시간 추이")
    print("=" * 40)
