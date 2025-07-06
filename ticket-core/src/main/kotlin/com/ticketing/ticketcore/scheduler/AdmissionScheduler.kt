package com.ticketing.ticketcore.scheduler

import com.ticketing.ticketcore.service.QueueService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AdmissionScheduler(
    private val queueService: QueueService
) {
    private val logger = LoggerFactory.getLogger(AdmissionScheduler::class.java)

    @Value("\${app.queue.admission-batch-size:100}")
    private val admissionBatchSize: Long = 100

    @Scheduled(fixedRate = 1000)
    fun admitUsersFromQueue() {
        try {
            val queueSize = queueService.getQueueSize()

            if (queueSize == 0L) {
                logger.debug("대기열이 비어 있어 스케줄러 패스")
                return
            }

            val admissionCount = minOf(admissionBatchSize, queueSize)

            val admittedUsers = queueService.admitUsers(admissionCount)

            if (admittedUsers.isNotEmpty()) {
                logger.info("스케줄러: ${admittedUsers.size}명을 대기열에서 추출하여 비동기 처리 요청. 남은 대기열: ${queueService.getQueueSize()}명")
            }

        } catch (e: Exception) {
            logger.error("스케줄러: 대기열 처리 중 오류 발생", e)
        }
    }

    @Scheduled(fixedRate = 30000)
    fun reportQueueStatus() {
        try {
            val queueSize = queueService.getQueueSize()
            logger.info("📊 대기열 상태 리포트 - 현재 대기 중인 사용자 수: $queueSize")

            if (queueSize > 1000) {
                logger.warn("⚠️ 대기열 사용자 수가 많음: $queueSize 명 - 배치 크기 조정 확인 필요")
            }

        } catch (e: Exception) {
            logger.error("대기열 상태 리포트 중 오류 발생", e)
        }
    }
}
