package com.ticketing.ticketapi.service

import com.ticketing.ticketapi.dto.AdmissionResponse
import com.ticketing.ticketapi.dto.QueueResponse
import com.ticketing.ticketapi.dto.QueueSizeResponse
import com.ticketing.ticketcore.service.QueueService as CoreQueueService
import org.springframework.stereotype.Service
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
            rank = rank,
            queueSize = getQueueSize().queueSize,
            message = if (rank != null) "대기열 진입 위치: ${rank}위" else "대기열 진입 실패"
        )
    }

    fun getQueueStatus(userId: Long): QueueResponse {
        val rank = getRank(userId)
        val queueSize = getQueueSize()

        val message = if (rank != null) {
            "대기열 ${rank}위입니다."
        } else {
            "사용자가 대기열에 존재하지 않음"
        }

        return QueueResponse(
            userId = userId,
            rank = rank,
            queueSize = queueSize.queueSize,
            message = message
        )
    }

    fun admitUsers(count: Long): AdmissionResponse {
        val admittedUsers = coreQueueService.admitUsers(count)
        val response = AdmissionResponse(
            admittedUsers = admittedUsers,
            admittedCount = admittedUsers.size,
            remainingQueueSize = getQueueSize().queueSize,
            message = "대기열 ${admittedUsers.size}명 허용"
        )
        return response
    }

    fun getQueueSize(): QueueSizeResponse {
        val response = QueueSizeResponse(coreQueueService.getQueueSize(), "현재 대기열 인원 수")
        return response
    }

    fun getRank(userId: Long): Long? {
        return coreQueueService.getQueueRank(userId)
    }

    fun isUserInQueue(userId: Long): Boolean {
        return coreQueueService.isUserInQueue(userId)
    }
}