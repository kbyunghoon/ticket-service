package com.ticketing.ticketcore.scheduler

import com.ticketing.ticketcore.service.QueueService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AdmissionScheduler(
    private val queueService: QueueService
) {
    private val logger = LoggerFactory.getLogger(AdmissionScheduler::class.java)
    
    companion object {
        private const val ADMISSION_COUNT_PER_SECOND = 100L
    }

    /**
     * 1초마다 100명씩 대기열에서 입장
     */
    // @Scheduled(fixedRate = 1000) // TODO: 나중에 활성화
    fun admitUsersFromQueue() {
        try {
            val queueSize = queueService.getQueueSize()
            
            if (queueSize == 0L) {
                logger.debug("대기열이 비어 있어 패스")
                return
            }
            
            val admissionCount = minOf(ADMISSION_COUNT_PER_SECOND, queueSize)
            val admittedUsers = queueService.admitUsers(admissionCount)
            
            if (admittedUsers.isNotEmpty()) {
                logger.info("대기열에 있는 사용자 ${admittedUsers.size}명 입장. 남은 대기열 인원 수: ${queueService.getQueueSize()}")
            }
            
        } catch (e: Exception) {
            logger.error("대기열에서 사용자 입장 처리 중 오류 발생", e)
        }
    }
}
