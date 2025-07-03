package com.ticketing.ticketcore.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@Service
class RequestMonitorService(
    private val redisTemplate: RedisTemplate<String, String>
) {

    @Value("\${app.queue.threshold.max-concurrent-requests:10}")
    private val maxConcurrentRequests: Int = 10

    private val overloadState = AtomicBoolean(false)

    companion object {
        private const val CURRENT_REQUESTS_KEY = "current_requests_count"
        private const val REQUEST_COUNTER_EXPIRE_SECONDS = 300L
    }

    fun incrementRequestCount(): Long {
        val currentCount = redisTemplate.opsForValue().increment(CURRENT_REQUESTS_KEY) ?: 0L
        redisTemplate.expire(CURRENT_REQUESTS_KEY, Duration.ofSeconds(REQUEST_COUNTER_EXPIRE_SECONDS))

        println("요청 카운트 증가: $currentCount")
        checkOverloadState(currentCount)

        return currentCount
    }

    fun decrementRequestCount(): Long {
        val beforeCount = getCurrentRequestCount()
        val currentCount = redisTemplate.opsForValue().decrement(CURRENT_REQUESTS_KEY) ?: 0L
        if (currentCount < 0) {
            redisTemplate.opsForValue().set(CURRENT_REQUESTS_KEY, "0")
            println("요청 카운트 감소: $beforeCount -> 0")
            checkOverloadState(0)
            return 0
        }

        println("요청 카운트 감소: $beforeCount -> $currentCount")
        checkOverloadState(currentCount)
        return currentCount
    }

    fun getCurrentRequestCount(): Long {
        val countStr = redisTemplate.opsForValue().get(CURRENT_REQUESTS_KEY)
        return countStr?.toLongOrNull() ?: 0L
    }

    fun isOverloaded(): Boolean {
        val currentCount = getCurrentRequestCount()
        val isCurrentlyOverloaded = currentCount >= maxConcurrentRequests
        if (overloadState.get() != isCurrentlyOverloaded) {
            overloadState.set(isCurrentlyOverloaded)
            println("🔄 과부하 상태 동기화: $isCurrentlyOverloaded (요청 수: $currentCount)")
        }

        return isCurrentlyOverloaded
    }

    fun isOverloadedIncludingQueue(queueSize: Long): Boolean {
        val currentCount = getCurrentRequestCount()
        val totalLoad = currentCount + queueSize
        val threshold = 10L
        val result = totalLoad >= threshold

        println("🔍 [DEBUG] === isOverloadedIncludingQueue 시작 ===")
        println("🔍 [DEBUG] currentCount: $currentCount")
        println("🔍 [DEBUG] queueSize: $queueSize")
        println("🔍 [DEBUG] totalLoad: $totalLoad")
        println("🔍 [DEBUG] threshold: $threshold")
        println("🔍 [DEBUG] result: $result")
        println("🔍 [DEBUG] === isOverloadedIncludingQueue 끝 ===")

        return result
    }

    private fun checkOverloadState(currentCount: Long) {
        val wasOverloaded = overloadState.get()
        val isCurrentlyOverloaded = currentCount >= maxConcurrentRequests

        if (wasOverloaded != isCurrentlyOverloaded) {
            overloadState.set(isCurrentlyOverloaded)
            logStateChange(isCurrentlyOverloaded, currentCount)
        }
    }

    private fun logStateChange(isOverloaded: Boolean, currentCount: Long) {
        if (isOverloaded) {
            println("🚨 시스템 과부하 상태 활성화 - 현재 요청 수: $currentCount, 임계값: $maxConcurrentRequests")
        } else {
            println("✅ 시스템 과부하 상태 비활성화 - 현재 요청 수: $currentCount, 임계값: $maxConcurrentRequests")
        }
    }

    fun getEstimatedWaitTime(queuePosition: Long): Long {
        val avgProcessingTimeSeconds = 5L
        val processingCapacity = maxConcurrentRequests.toLong()

        return (queuePosition * avgProcessingTimeSeconds) / processingCapacity
    }
}
