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
        
        redis.call('LREM', queueKey, 0, userId)
        
        redis.call('RPUSH', queueKey, userId)
        
        return redis.call('LLEN', queueKey)
    """.trimIndent(), Long::class.java
    )

    private val removeTopEntriesScript = RedisScript.of<List<*>>(
        """
    local queueKey = KEYS[1]
    local count = tonumber(ARGV[1])
    
    local result = {}
    for i = 1, count do
        local userId = redis.call('LPOP', queueKey)
        if userId then
            table.insert(result, userId)
        else
            break
        end
    end
    return result
""".trimIndent(), List::class.java
    )

    fun addEntry(userId: Long): Long? {
        val rank = redisTemplate.execute(addEntryScript, listOf(queueKey), userId.toString())
        return if (rank != null && rank > 0) rank else null
    }

    fun getRank(userId: Long): Long? {
        val queueList = redisTemplate.opsForList().range(queueKey, 0, -1) ?: return null
        val index = queueList.indexOf(userId.toString())
        return if (index >= 0) (index + 1).toLong() else null
    }

    @Suppress("UNCHECKED_CAST")
    fun getAndRemoveTopEntries(count: Long): List<Long> {
        val result: List<*>? = redisTemplate.execute(removeTopEntriesScript, listOf(queueKey), count.toString())
        return (result as? List<String>)?.map { it.toLong() } ?: emptyList()
    }

    fun getQueueSize(): Long {
        return redisTemplate.opsForList().size(queueKey) ?: 0
    }

    fun isUserInQueue(userId: Long): Boolean {
        val queueList = redisTemplate.opsForList().range(queueKey, 0, -1) ?: return false
        return queueList.contains(userId.toString())
    }

    fun removeUser(userId: Long): Boolean {
        val removed = redisTemplate.opsForList().remove(queueKey, 1, userId.toString())
        return removed != null && removed > 0
    }

    fun clearQueue() {
        redisTemplate.delete(queueKey)
    }
}
