from locust import HttpUser, task, between, events
import json
import random
import time
from datetime import datetime

class QueueEnterUser(HttpUser):
    wait_time = between(0.1, 0.5)
    
    def on_start(self):
        """테스트 시작 시 초기화"""
        self.user_id = random.randint(1000, 9999)
        self.event_id = "EVENT_001"
        print(f"Queue Enter 테스트 사용자 {self.user_id} 시작")
    
    @task
    def enter_queue(self):
        """/api/queue/enter 요청 테스트"""
        payload = {
            "eventId": self.event_id,
            "userId": self.user_id
        }
        
        headers = {
            "Content-Type": "application/json",
            "X-User-ID": str(self.user_id)
        }
        
        with self.client.post(
            "/api/queue/enter", 
            json=payload, 
            headers=headers,
            catch_response=True
        ) as response:
            if response.status_code in [200, 202]:
                try:
                    queue_response = response.json()
                    token = queue_response.get('token', 'N/A')
                    position = queue_response.get('queuePosition', 'N/A')
                    wait_time = queue_response.get('estimatedWaitTimeSeconds', 'N/A')
                    
                    status_msg = "즉시 처리" if response.status_code == 200 else "대기열 등록"
                    print(f"🎫 [{self.user_id}] {status_msg} - Token: {token}, Position: {position}, Wait: {wait_time}s")
                    response.success()
                except Exception as e:
                    print(f"❌ [{self.user_id}] 응답 파싱 실패: {e}")
                    response.failure(f"Queue enter response parsing failed: {e}")
            else:
                print(f"❌ [{self.user_id}] 대기열 진입 실패: {response.status_code} - {response.text}")
                response.failure(f"Queue enter failed: {response.status_code}")

class QueueEnterStressUser(HttpUser):
    """대기열 진입 스트레스 테스트용 - 더 빠른 요청"""
    wait_time = between(0, 0.2)
    weight = 5
    
    def on_start(self):
        self.user_id = random.randint(10000, 99999)
        self.event_id = "EVENT_STRESS"
    
    @task
    def rapid_queue_enter(self):
        """빠른 연속 대기열 진입 요청"""
        payload = {
            "eventId": self.event_id,
            "userId": self.user_id
        }
        
        headers = {
            "Content-Type": "application/json",
            "X-User-ID": str(self.user_id)
        }
        
        with self.client.post(
            "/api/queue/enter", 
            json=payload, 
            headers=headers,
            catch_response=True
        ) as response:
            if response.status_code in [200, 202]:
                try:
                    queue_response = response.json()
                    position = queue_response.get('queuePosition', 'N/A')
                    status_msg = "즉시" if response.status_code == 200 else "대기열"
                    print(f"🔥 [STRESS-{self.user_id}] {status_msg} 진입 - Position: {position}")
                    response.success()
                except:
                    response.success()
            else:
                response.failure(f"Stress queue enter failed: {response.status_code}")

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("🎯 /api/queue/enter 전용 부하 테스트 시작")
    print("=" * 60)
    print("테스트 대상: /api/queue/enter")
    print("목표: Kafka Queue에 메시지 적재 및 LAG 발생")
    print("=" * 60)

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("=" * 60)
    print("Queue Enter 부하 테스트 완료")

if __name__ == "__main__":
    print("/api/queue/enter 전용 Locust 스크립트")
    print("locust -f locust_test.py --host=http://localhost:8080")
    print("\n테스트 시나리오:")
    print("1. QueueEnterUser - 일반적인 대기열 진입 테스트")
    print("2. QueueEnterStressUser - 빠른 연속 대기열 진입 테스트")