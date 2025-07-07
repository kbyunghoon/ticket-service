from locust import HttpUser, task, between, events
import random
import re
from urllib.parse import urlparse, parse_qs

class TicketingUser(HttpUser):
    """실제 사용자 플로우를 시뮬레이션하는 테스트"""
    wait_time = between(1, 3)
    
    def on_start(self):
        """테스트 시작 시 초기화"""
        self.user_id = random.randint(1000, 99999)
        self.access_token = None
        print(f"사용자 {self.user_id} 테스트 시작")
    
    @task
    def full_user_journey(self):
        """전체 사용자 여정 테스트"""
        
        # 1. 메인 입장 페이지 접속
        with self.client.get("/entry", catch_response=True) as response:
            if response.status_code == 200:
                print(f"✅ [{self.user_id}] 입장 페이지 로드 성공")
                response.success()
            else:
                print(f"❌ [{self.user_id}] 입장 페이지 로드 실패: {response.status_code}")
                response.failure(f"Entry page failed: {response.status_code}")
                return
        
        # 2. 대기열 진입 요청 (HTML 폼 제출)
        form_data = {"userId": self.user_id}
        
        with self.client.post(
            "/entry/join", 
            data=form_data,
            allow_redirects=False,
            catch_response=True
        ) as response:
            if response.status_code in [302, 303]:
                redirect_url = response.headers.get('Location', '')
                print(f"[{self.user_id}] 리다이렉트: {redirect_url}")
                
                if '/entry/waiting' in redirect_url:
                    print(f"[{self.user_id}] 대기열 진입")
                    self.handle_waiting_room(redirect_url)
                    response.success()
                    
                elif '/seat' in redirect_url:
                    print(f"[{self.user_id}] 바로 입장 허용")
                    self.handle_seat_page(redirect_url)
                    response.success()
                    
                else:
                    print(f"[{self.user_id}] 예상치 못한 리다이렉트: {redirect_url}")
                    response.failure(f"Unexpected redirect: {redirect_url}")
                    
            else:
                print(f"❌ [{self.user_id}] 진입 실패: {response.status_code}")
                response.failure(f"Join failed: {response.status_code}")
    
    def handle_waiting_room(self, waiting_url):
        """대기실 처리"""
        # 대기실 페이지 로드
        with self.client.get(waiting_url, catch_response=True) as response:
            if response.status_code == 200:
                print(f"[{self.user_id}] 대기실 페이지 로드")
                response.success()
            else:
                print(f"❌ [{self.user_id}] 대기실 페이지 로드 실패")
                response.failure("Waiting page failed")
                return
        
        # 순위 조회 API 호출 (JavaScript에서 주기적으로 호출하는 것을 시뮬레이션)
        max_polls = 10
        for poll_count in range(max_polls):
            with self.client.get(f"/entry/api/rank/{self.user_id}", catch_response=True) as response:
                if response.status_code == 200:
                    try:
                        rank_data = response.json()
                        rank = rank_data.get('rank')
                        is_admitted = rank_data.get('isAdmitted', False)
                        access_token = rank_data.get('accessToken')
                        
                        if is_admitted and access_token:
                            print(f"🎉 [{self.user_id}] 입장 허용 토큰: {access_token[:20]}...")
                            self.access_token = access_token
                            seat_url = f"/seat?userId={self.user_id}&token={access_token}"
                            self.handle_seat_page(seat_url)
                            response.success()
                            return
                        else:
                            print(f"[{self.user_id}] 대기 중... 순위: {rank}")
                            response.success()
                            
                    except Exception as e:
                        print(f"❌ [{self.user_id}] 순위 응답 파싱 실패: {e}")
                        response.failure(f"Rank parsing failed: {e}")
                        return
                else:
                    print(f"❌ [{self.user_id}] 순위 조회 실패: {response.status_code}")
                    response.failure("Rank check failed")
                    return
            
            # 폴링 간격 (실제 JavaScript는 1초마다 호출)
            self.wait_time = between(1, 2)
        
        print(f"[{self.user_id}] 대기 시간 초과")
    
    def handle_seat_page(self, seat_url):
        """좌석 선택 페이지 처리"""
        with self.client.get(seat_url, catch_response=True) as response:
            if response.status_code == 200:
                print(f"✅ [{self.user_id}] 좌석 페이지 접근 성공")
                response.success()
                
                self.simulate_seat_completion()
                
            elif response.status_code == 302:
                redirect_url = response.headers.get('Location', '')
                print(f"❌ [{self.user_id}] 좌석 접근 실패 - 리다이렉트: {redirect_url}")
                response.failure("Seat access denied")
            else:
                print(f"❌ [{self.user_id}] 좌석 페이지 로드 실패: {response.status_code}")
                response.failure(f"Seat page failed: {response.status_code}")
    
    def simulate_seat_completion(self):
        """좌석 선택 완료 시뮬레이션"""
        self.wait_time = between(5, 15)
        
        completion_data = {"userId": self.user_id}
        
        with self.client.post(
            "/seat/complete",
            data=completion_data,
            allow_redirects=False,
            catch_response=True
        ) as response:
            if response.status_code in [302, 303]:
                print(f"✅ [{self.user_id}] 좌석 선택 완료")
                response.success()
            else:
                print(f"❌ [{self.user_id}] 좌석 선택 완료 실패: {response.status_code}")
                response.failure("Seat completion failed")


class StressUser(HttpUser):
    """빠른 속도로 진입을 시도하는 스트레스 테스트"""
    wait_time = between(0.1, 0.5)
    weight = 3
    
    def on_start(self):
        self.user_id = random.randint(100000, 999999)
    
    @task
    def rapid_entry_attempt(self):
        """빠른 진입 시도"""
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
                    print(f"[STRESS-{self.user_id}] 대기열 진입")
                elif '/seat' in redirect_url:
                    print(f"[STRESS-{self.user_id}] 바로 진입")
                response.success()
            else:
                response.failure(f"Stress entry failed: {response.status_code}")


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("실제 사용자 플로우 부하 테스트 시작")
    print("=" * 60)
    print("테스트 시나리오:")
    print("1. TicketingUser - 전체 사용자 여정 (입장 → 대기열/바로진입 → 좌석선택)")
    print("2. StressUser - 빠른 연속 진입 시도")
    print("=" * 60)

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("=" * 60)
    print("실제 사용자 플로우 부하 테스트 완료")

if __name__ == "__main__":
    print("실제 사용자 플로우 기반 Locust 스크립트")
    print("locust -f locust_test.py --host=http://localhost:8080")
    print("\n권장 설정:")
    print("- 사용자 수: 50-200명")
    print("- Spawn rate: 5-10 users/sec")
    print("- 테스트 시간: 5-10분")