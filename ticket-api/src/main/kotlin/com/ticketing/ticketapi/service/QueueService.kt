package com.ticketing.ticketapi.service

import com.ticketing.ticketapi.dto.AdmissionResponse
import com.ticketing.ticketapi.dto.QueueResponse
import com.ticketing.ticketapi.dto.QueueSizeResponse
import com.ticketing.ticketcore.service.QueueService as CoreQueueService
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

@Service
class QueueApiService(
    private val coreQueueService: CoreQueueService
) {
    private val userIdGenerator = AtomicLong(1)

    fun enterQueue(userId: Long?): QueueResponse {
        val actualUserId = userId ?: userIdGenerator.getAndIncrement()
        val rank = coreQueueService.enterQueue(actualUserId)

        return QueueResponse(
            userId = actualUserId,
            position = rank ?: 0L,
            estimatedWaitTime = if (rank != null) rank * 5 else 0L,
            isActive = rank != null,
            enteredAt = LocalDateTime.now().toString()
        )
    }

    fun getQueueStatus(userId: Long): QueueResponse {
        val rank = getRank(userId)
        
        return QueueResponse(
            userId = userId,
            position = rank ?: 0L,
            estimatedWaitTime = if (rank != null) rank * 5 else 0L,
            isActive = rank != null,
            enteredAt = LocalDateTime.now().toString()
        )
    }

    fun admitUsers(count: Long): AdmissionResponse {
        val admittedUsers = coreQueueService.admitUsers(count)
        val response = AdmissionResponse(
            admittedCount = admittedUsers.size.toLong(),
            remainingQueueSize = getQueueSize().totalSize,
            message = "대기열 ${admittedUsers.size}명 허용"
        )
        return response
    }

    fun getQueueSize(): QueueSizeResponse {
        val queueSize = coreQueueService.getQueueSize()
        return QueueSizeResponse(
            totalSize = queueSize,
            activeUsers = queueSize,
            estimatedProcessingTime = queueSize * 5 // 5초 * 대기 인원
        )
    }

    fun getRank(userId: Long): Long? {
        return coreQueueService.getQueueRank(userId)
    }

    fun isUserInQueue(userId: Long): Boolean {
        return coreQueueService.isUserInQueue(userId)
    }
}
