# 티켓팅 서비스 시스템
- 멀티모듈로 구현한 프로젝트이며, 해당 프로젝트는 `Kotlin`, `Kafka`, `동시성`, `분산락`을 연습하기 위해 진행하였습니다.

## 🏗️ 시스템 아키텍처
```
모듈 구조
ticketing-service/
├── ticket-api/         # API 계층 (REST 컨트롤러)
├── ticket-core/        # 비즈니스 로직 및 인프라
├── ticket-common/      # 공통 모듈 (Kafka, 유틸리티)
└── docker-compose.yml  # 인프라 환경 설정
```

기술 스택
- 언어: Kotlin
- 프레임워크: Spring Boot 3.3.1
- 데이터베이스: Redis (대기열), MySQL
- 메시지 큐: Apache Kafka(구현 예정)
- 분산락: Redisson
- 컨테이너: Docker Compose

---
## 대기열 시스템
- 대용량 티켓팅 서비스의 트래픽을 효율적으로 관리하기 위한 대기열 시스템을 구현하였습니다.
- Redis 기반으로 대기열과 분산락을 활용하여 동시성 문제를 해결하였습니다.

### 🚀 주요 기능
1. 대기열 관리
- 사용자를 대기열에 추가하고 순위 반환 
- 실시간 대기열 순위 확인 
- 배치 단위로 대기열에서 사용자 입장 처리 
- 전체 대기열 크기 조회
2. 동시성 제어
- Redisson을 활용한 멀티 서버 환경에서의 동시성 제어(분산 락)
- 원자적 연산을 통한 데이터 일관성 보장(원자성을 위한 Redis Lua 스크립트 적용)
- 효율적인 사용자 입장 처리(배치 처리)
3. 모니터링 및 관리
- 스케줄러: 자동화된 사용자 입장 처리 (현재 비활성화 상태)
- 로깅: 구조화된 로그를 통한 시스템 모니터링
- 헬스체크: Spring Boot Actuator를 통한 시스템 상태 확인

### 📋 API 명세
#### 대기열 API
- 대기열 진입 요청 및 응답
```http request
POST /api/queue/enter?userId={userId}
```
```json
{
  "userId": 12345,
  "rank": 150,
  "queueSize": 10000,
  "message": "대기열 진입 위치: 150위"
}
```
---
- 대기열 상태 조회
```http request
GET /api/queue/status?userId={userId}
```
```json
{
  "userId": 12345,
  "rank": 120,
  "queueSize": 9800,
  "message": "대기열 120위입니다."
}
```
---
- 사용자 입장 처리
```http request
POST /api/queue/admit?count={count}
```
```json
{
  "admittedUsers": [1, 2, 3, 4, 5],
  "admittedCount": 5,
  "remainingQueueSize": 9995,
  "message": "대기열 5명 허용"
}
```
---
- 대기열 크기 조회
```http request
GET /api/queue/size
```
```json
{
  "queueSize": 10000,
  "message": "현재 대기열 인원 수"
}
```
---
## 좌석 티켓팅(구현 중)