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

    @Value("\${app.queue.threshold.max-concurrent-requests:100}")
    private val maxConcurrentRequests: Int = 100

    private val overloadState = AtomicBoolean(false)

    companion object {
        private const val CURRENT_REQUESTS_KEY = "current_requests_count"
        private const val REQUEST_COUNTER_EXPIRE_SECONDS = 300L

        private const val INCREMENT_AND_CHECK_SCRIPT = """
            local key = KEYS[1]
            local threshold = tonumber(ARGV[1])
            local expire_seconds = tonumber(ARGV[2])
            
            local current = redis.call('INCR', key)
            redis.call('EXPIRE', key, expire_seconds)
            
            local is_overloaded = current >= threshold
            
            return {current, is_overloaded and 1 or 0}
        """

        private const val DECREMENT_SCRIPT = """
            local key = KEYS[1]
            local current = redis.call('GET', key)
            if current == false then
                return 0
            end
            
            current = tonumber(current)
            if current <= 0 then
                redis.call('SET', key, '0')
                return 0
            else
                return redis.call('DECR', key)
            end
        """

        private const val GET_AND_CHECK_SCRIPT = """
            local key = KEYS[1]
            local threshold = tonumber(ARGV[1])
            
            local current = redis.call('GET', key)
            if current == false then
                current = 0
            else
                current = tonumber(current)
            end
            
            local is_overloaded = current >= threshold
            
            return {current, is_overloaded and 1 or 0}
        """
    }

    fun incrementRequestCount(): Long {
        val result = redisTemplate.execute<List<Long>> { connection ->
            val scriptResult = connection.eval(
                INCREMENT_AND_CHECK_SCRIPT.toByteArray(),
                org.springframework.data.redis.connection.ReturnType.MULTI,
                1,
                CURRENT_REQUESTS_KEY.toByteArray(),
                maxConcurrentRequests.toString().toByteArray(),
                REQUEST_COUNTER_EXPIRE_SECONDS.toString().toByteArray()
            ) as List<Long>
            scriptResult
        } ?: listOf(0L, 0L)
        
        val currentCount = result[0]
        val isOverloaded = result[1] == 1L
        
        println("요청 카운트 증가: $currentCount (과부하: $isOverloaded)")
        updateOverloadState(isOverloaded, currentCount)
        
        return currentCount
    }

    fun decrementRequestCount(): Long {
        val currentCount = redisTemplate.execute<Long> { connection ->
            connection.eval(
                DECREMENT_SCRIPT.toByteArray(),
                org.springframework.data.redis.connection.ReturnType.INTEGER,
                1,
                CURRENT_REQUESTS_KEY.toByteArray()
            ) as Long
        } ?: 0L
        
        println("요청 카운트 감소: $currentCount")

        val isCurrentlyOverloaded = currentCount >= maxConcurrentRequests
        updateOverloadState(isCurrentlyOverloaded, currentCount)
        
        return currentCount
    }

    fun getCurrentRequestCount(): Long {
        val countStr = redisTemplate.opsForValue().get(CURRENT_REQUESTS_KEY)
        return countStr?.toLongOrNull() ?: 0L
    }

    fun isOverloaded(): Boolean {
        val result = redisTemplate.execute<List<Long>> { connection ->
            val scriptResult = connection.eval(
                GET_AND_CHECK_SCRIPT.toByteArray(),
                org.springframework.data.redis.connection.ReturnType.MULTI,
                1,
                CURRENT_REQUESTS_KEY.toByteArray(),
                maxConcurrentRequests.toString().toByteArray()
            ) as List<Long>
            scriptResult
        } ?: listOf(0L, 0L)
        
        val currentCount = result[0]
        val isCurrentlyOverloaded = result[1] == 1L
        
        println("🔍 [DEBUG] === 과부하 상태 확인 ===")
        println("🔍 [DEBUG] currentCount: $currentCount")
        println("🔍 [DEBUG] maxConcurrentRequests: $maxConcurrentRequests")
        println("🔍 [DEBUG] isOverloaded: $isCurrentlyOverloaded")
        println("🔍 [DEBUG] === 과부하 상태 확인 끝 ===")
        
        updateOverloadState(isCurrentlyOverloaded, currentCount)
        
        return isCurrentlyOverloaded
    }

    private fun updateOverloadState(isCurrentlyOverloaded: Boolean, currentCount: Long) {
        val wasOverloaded = overloadState.get()
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
