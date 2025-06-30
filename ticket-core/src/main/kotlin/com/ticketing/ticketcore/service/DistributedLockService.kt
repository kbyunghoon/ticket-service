package com.ticketing.ticketcore.service

import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class DistributedLockService(
    private val redissonClient: RedissonClient
) {
    private val logger = LoggerFactory.getLogger(DistributedLockService::class.java)

    /**
     * 분산락
     */
    fun <T> executeWithLock(
        lockKey: String,
        waitTimeSeconds: Long = 10,
        leaseTimeSeconds: Long = 30,
        action: () -> T
    ): LockResult<T> {
        val lock = redissonClient.getLock(lockKey)
        
        return try {
            if (lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS)) {
                logger.debug("분산락 획득 성공: $lockKey")
                val result = action()
                LockResult.Success(result)
            } else {
                logger.warn("분산락 획득 실패: $lockKey (대기시간 ${waitTimeSeconds}초 초과)")
                LockResult.LockFailed("대기시간 내 락 획득 실패")
            }
        } catch (e: Exception) {
            logger.error("분산락 실행 중 오류 발생: $lockKey", e)
            LockResult.Error(e.message ?: "알 수 없는 오류 발생")
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
                logger.debug("분산락 해제 완료: $lockKey")
            }
        }
    }

    /**
     * 대기열 관련 분산락 실행
     */
    fun executeQueueOperation(
        eventId: String,
        operation: String,
        action: () -> Unit
    ): Boolean {
        val lockKey = "queue:$operation:$eventId"
        
        return when (val result = executeWithLock(lockKey, action = action)) {
            is LockResult.Success -> true
            is LockResult.LockFailed -> {
                logger.warn("대기열 작업 실패 - 락 타임아웃: 이벤트 $eventId 의 $operation 작업")
                false
            }
            is LockResult.Error -> {
                logger.error("대기열 작업 실패 - 오류 발생: 이벤트 $eventId 의 $operation 작업, 오류: ${result.message}")
                false
            }
        }
    }
}

sealed class LockResult<out T> {
    data class Success<T>(val value: T) : LockResult<T>()
    data class LockFailed(val message: String) : LockResult<Nothing>()
    data class Error(val message: String) : LockResult<Nothing>()
}
