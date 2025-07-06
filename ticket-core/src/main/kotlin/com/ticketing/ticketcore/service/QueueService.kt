package com.ticketing.ticketcore.service

import com.ticketing.ticketcore.infra.kafka.UserAdmissionProducer
import com.ticketing.ticketcore.infra.redis.RedisQueueManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class QueueService(
    private val redisQueueManager: RedisQueueManager,
    private val userAdmissionProducer: UserAdmissionProducer,
    private val distributedLockService: DistributedLockService
) {
    private val logger = LoggerFactory.getLogger(QueueService::class.java)

    fun enterQueue(userId: Long): Long? {
        val rank = redisQueueManager.addEntry(userId)
        logger.info("사용자 $userId 대기열 입장 - 대기열 순위 : $rank")
        return rank
    }

    fun getQueueRank(userId: Long): Long? {
        return redisQueueManager.getRank(userId)
    }

    fun admitUsers(count: Long): List<Long> {
        val lockKey = "queue:admit:users"
        val batchId = UUID.randomUUID().toString()

        val admittedUsers = when (val result = distributedLockService.executeWithLock(
            lockKey,
            waitTimeSeconds = 10,
            leaseTimeSeconds = 5
        ) {
            val users = redisQueueManager.getAndRemoveTopEntries(count)
            logger.info("대기열에서 사용자 ${users.size}명 추출 완료 - batchId: $batchId")
            users
        }) {
            is LockResult.Success -> {
                logger.info("대기열 추출 성공 - ${result.value.size}명, batchId: $batchId")
                result.value
            }

            is LockResult.LockFailed -> {
                logger.warn("대기열 추출을 위한 락 획득 실패 - batchId: $batchId, ${result.message}")
                emptyList()
            }

            is LockResult.Error -> {
                logger.error("대기열 추출 중 오류 발생 - batchId: $batchId, ${result.message}")
                emptyList()
            }
        }

        if (admittedUsers.isNotEmpty()) {
            logger.info("비동기 사용자 입장 처리 시작 - ${admittedUsers.size}명, batchId: $batchId")
            userAdmissionProducer.sendBatchUserAdmissionEvents(admittedUsers, batchId)
        }

        return admittedUsers
    }

    fun getQueueSize(): Long {
        return redisQueueManager.getQueueSize()
    }

    fun isUserInQueue(userId: Long): Boolean {
        return redisQueueManager.isUserInQueue(userId)
    }
}
