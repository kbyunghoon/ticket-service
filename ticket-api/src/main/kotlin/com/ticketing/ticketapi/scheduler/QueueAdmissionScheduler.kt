package com.ticketing.ticketapi.scheduler

import com.ticketing.ticketcore.service.QueueService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    value = ["app.scheduler.queue-admission.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class QueueAdmissionScheduler(
    private val queueService: QueueService
) {
    private val logger = LoggerFactory.getLogger(QueueAdmissionScheduler::class.java)

    companion object {
        private const val ADMISSION_BATCH_SIZE = 1L
    }

    @Scheduled(fixedRate = 1000)
    fun processQueueAdmission() {
        try {
            val queueSize = queueService.getQueueSize()

            if (queueSize == 0L) {
                logger.debug("대기열이 비어있음")
                return
            }

            logger.info("대기열 입장 처리 시작 - 현재 대기열 크기: $queueSize")
            val randomBatchSize = (1..10).random()
            val admittedUsers = queueService.admitUsers(randomBatchSize.toLong())

            if (admittedUsers.isNotEmpty()) {
                logger.info("대기열 입장 처리 완료 - 입장된 사용자 수: ${admittedUsers.size}, 사용자 ID: $admittedUsers")
            }

            val remainingQueueSize = queueService.getQueueSize()
            logger.info("대기열 입장 처리 후 - 남은 대기열 크기: $remainingQueueSize")

        } catch (e: Exception) {
            logger.error("대기열 입장 처리 중 오류 발생", e)
        }
    }

    @Scheduled(fixedRate = 30000)
    fun reportQueueStatus() {
        try {
            val queueSize = queueService.getQueueSize()
            logger.info("대기열 상태 리포트 - 현재 대기 중인 사용자 수: $queueSize")
        } catch (e: Exception) {
            logger.error("대기열 상태 리포트 중 오류 발생", e)
        }
    }
}
