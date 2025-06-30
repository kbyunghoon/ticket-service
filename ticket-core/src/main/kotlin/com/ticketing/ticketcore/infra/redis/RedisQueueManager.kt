package com.ticketing.ticketcore.infra.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

@Component
class RedisQueueManager(
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val queueKey = "waiting_queue"

    private val addEntryScript = RedisScript.of<Long>(
        """
        local queueKey = KEYS[1]
        local userId = ARGV[1]
        
        redis.call('LREM', queueKey, 1, userId)
        
        redis.call('LPUSH', queueKey, userId)
        
        local queueList = redis.call('LRANGE', queueKey, 0, -1)
        for i = #queueList, 1, -1 do
            if queueList[i] == userId then
                return #queueList - i + 1
            end
        end
        return -1
    """.trimIndent(), Long::class.java
    )

    private val removeTopEntriesScript = RedisScript.of<List<*>>(
        """
    local queueKey = KEYS[1]
    local count = tonumber(ARGV[1])
    
    local result = {}
    for i = 1, count do
        local userId = redis.call('RPOP', queueKey)
        if userId then
            table.insert(result, userId)
        else
            break
        end
    end
    return result
""".trimIndent(), List::class.java
    )

    /**
     * 대기열에 사용자 추가
     */
    fun addEntry(userId: Long): Long? {
        val rank = redisTemplate.execute(addEntryScript, listOf(queueKey), userId.toString())
        return if (rank != null && rank > 0) rank else null
    }

    /**
     * 사용자의 대기열 순위 조회
     */
    fun getRank(userId: Long): Long? {
        val queueList = redisTemplate.opsForList().range(queueKey, 0, -1) ?: return null
        val index = queueList.reversed().indexOf(userId.toString())
        return if (index >= 0) (index + 1).toLong() else null
    }

    /**
     * 대기열에서 상위 N명을 제거하고 반환
     */
    @Suppress("UNCHECKED_CAST")
    fun getAndRemoveTopEntries(count: Long): List<Long> {
        val result: List<*>? = redisTemplate.execute(removeTopEntriesScript, listOf(queueKey), count.toString())
        return (result as? List<String>)?.map { it.toLong() } ?: emptyList()
    }

    /**
     * 현재 대기열 크기 조회
     */
    fun getQueueSize(): Long {
        return redisTemplate.opsForList().size(queueKey) ?: 0
    }

    /**
     * 사용자가 대기열에 있는지 확인
     */
    fun isUserInQueue(userId: Long): Boolean {
        val queueList = redisTemplate.opsForList().range(queueKey, 0, -1) ?: return false
        return queueList.contains(userId.toString())
    }

    /**
     * 특정 사용자를 대기열에서 제거
     */
    fun removeUser(userId: Long): Boolean {
        val removed = redisTemplate.opsForList().remove(queueKey, 1, userId.toString())
        return removed != null && removed > 0
    }

    /**
     * 대기열 전체 초기화
     */
    fun clearQueue() {
        redisTemplate.delete(queueKey)
    }
}
