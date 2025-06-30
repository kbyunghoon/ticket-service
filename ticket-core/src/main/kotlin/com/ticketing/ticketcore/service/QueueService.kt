package com.ticketing.ticketcore.service

import com.ticketing.ticketcore.infra.redis.RedisQueueManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QueueService(
    private val redisQueueManager: RedisQueueManager,
    private val userAdmissionService: UserAdmissionService,
    private val distributedLockService: DistributedLockService
) {
    private val logger = LoggerFactory.getLogger(QueueService::class.java)

    /**
     * 사용자를 대기열에 진입
     */
    fun enterQueue(userId: Long): Long? {
        val rank = redisQueueManager.addEntry(userId)
        logger.info("사용자 $userId 대기열 입장 - 대기열 순위 : $rank")
        return rank
    }

    /**
     * 사용자의 대기열 순위를 조회
     */
    fun getQueueRank(userId: Long): Long? {
        return redisQueueManager.getRank(userId)
    }

    /**
     * 지정된 수만큼 사용자를 대기열에서 입장
     * 분산락 적용
     */
    fun admitUsers(count: Long): List<Long> {
        val lockKey = "queue:admit:users"

        return when (val result =
            distributedLockService.executeWithLock(lockKey, waitTimeSeconds = 10, leaseTimeSeconds = 30) {
                // 크리티컬 섹션: 한 번에 하나의 서버에서만 실행
                val admittedUsers = redisQueueManager.getAndRemoveTopEntries(count)

                // 입장 처리된 사용자들에 대해 직접 처리
                admittedUsers.forEach { userId ->
                    userAdmissionService.processUserAdmission(userId)
                    logger.info("대기열 - 사용자 $userId 입장")
                }

                admittedUsers
            }) {
            is LockResult.Success -> {
                logger.info("대기열에서 사용자 ${result.value.size}명 입장")
                result.value
            }

            is LockResult.LockFailed -> {
                logger.warn("사용자 입장 처리를 위한 락 획득에 실패 : ${result.message}")
                emptyList()
            }

            is LockResult.Error -> {
                logger.error("사용자 입장 처리 중 오류 발생 : ${result.message}")
                emptyList()
            }
        }
    }

    /**
     * 현재 대기열 길이 조회
     */
    fun getQueueSize(): Long {
        return redisQueueManager.getQueueSize()
    }

    /**
     * 사용자가 대기열 존재하는지 확인
     */
    fun isUserInQueue(userId: Long): Boolean {
        return redisQueueManager.isUserInQueue(userId)
    }
}
