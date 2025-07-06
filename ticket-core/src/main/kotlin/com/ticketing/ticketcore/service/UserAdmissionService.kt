package com.ticketing.ticketcore.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserAdmissionService(
    private val requestMonitorService: RequestMonitorService
) {
    private val logger = LoggerFactory.getLogger(UserAdmissionService::class.java)

    fun processUserAdmission(userId: Long) {
        logger.info("사용자 $userId 입장 처리 시작")

        try {
            // 대기열에서 입장하는 사용자의 요청 카운트 증가
            val currentCount = requestMonitorService.incrementRequestCount()
            logger.info("사용자 $userId 입장 - 현재 요청 수: $currentCount")

            // TODO: 여기에 입장 후속 처리 로직 구현
            // 1. 입장 토큰 발급
            // 2. 세션 생성
            // 3. 입장 알림 (WebSocket, SSE 등)
            // 4. 통계 업데이트

            logger.info("사용자 $userId 입장 처리 성공")

        } catch (e: Exception) {
            logger.error("사용자 $userId 입장 처리 실패", e)
            throw e
        }
    }
}
