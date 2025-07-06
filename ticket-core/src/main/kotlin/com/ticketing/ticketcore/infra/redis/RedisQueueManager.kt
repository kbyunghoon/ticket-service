package com.ticketing.ticketcore.infra.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

@Component
class RedisQueueManager(
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val queueKey = "waiting_queue_sorted"

    private val addEntryScript = RedisScript.of(
        """
        local queueKey = KEYS[1]
        local userId = ARGV[1]
        local timestamp = tonumber(ARGV[2])
        
        local existingScore = redis.call('ZSCORE', queueKey, userId)
        if existingScore then
            return redis.call('ZCARD', queueKey)
        end
        
        redis.call('ZADD', queueKey, timestamp, userId)
        
        return redis.call('ZCARD', queueKey)
        """.trimIndent(),
        Long::class.java
    )

    private val removeTopEntriesScript = RedisScript.of(
        """
        local queueKey = KEYS[1]
        local count = tonumber(ARGV[1])
        
        local result = {}
        for i = 1, count do
            local popped = redis.call('ZPOPMIN', queueKey)
            if popped[1] then
                table.insert(result, popped[1])
            else
                break
            end
        end
        return result
        """.trimIndent(),
        List::class.java
    )

    fun addEntry(userId: Long): Long? {
        val timestamp = System.currentTimeMillis().toDouble()
        val queueSize = redisTemplate.execute(
            addEntryScript,
            listOf(queueKey),
            userId.toString(),
            timestamp.toString()
        )
        return queueSize.takeIf { it > 0 }
    }

    fun getRank(userId: Long): Long? {
        val rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString())
        return rank?.let { it + 1 } // 0-based → 1-based
    }

    @Suppress("UNCHECKED_CAST")
    fun getAndRemoveTopEntries(count: Long): List<Long> {
        val result = redisTemplate.execute(
            removeTopEntriesScript,
            listOf(queueKey),
            count.toString()
        ) as? List<String>

        return result?.map { it.toLong() } ?: emptyList()
    }

    fun getQueueSize(): Long {
        return redisTemplate.opsForZSet().zCard(queueKey) ?: 0L
    }

    fun isUserInQueue(userId: Long): Boolean {
        val score = redisTemplate.opsForZSet().score(queueKey, userId.toString())
        return score != null
    }

    fun removeUser(userId: Long): Boolean {
        val removed = redisTemplate.opsForZSet().remove(queueKey, userId.toString())
        return removed != null && removed > 0
    }

    fun clearQueue() {
        redisTemplate.delete(queueKey)
    }

    /**
     * 디버깅용: 대기열 상위 N명 조회 (제거하지 않음)
     * @param count 조회할 사용자 수
     * @return 상위 N명의 사용자 ID와 score
     */
    fun peekTopEntries(count: Long): List<Pair<Long, Double>> {
        val entries = redisTemplate.opsForZSet().rangeWithScores(queueKey, 0, count - 1)
        return entries?.map {
            Pair(it.value!!.toLong(), it.score!!)
        } ?: emptyList()
    }

    /**
     * 디버깅용: 특정 사용자의 대기열 정보 조회
     * @param userId 사용자 ID
     * @return 사용자의 순위와 등록 시간 정보
     */
    fun getUserInfo(userId: Long): Map<String, Any?>? {
        val rank = getRank(userId)
        val score = redisTemplate.opsForZSet().score(queueKey, userId.toString())

        return if (rank != null && score != null) {
            mapOf(
                "userId" to userId,
                "rank" to rank,
                "registeredAt" to score,
                "waitingTime" to (System.currentTimeMillis() - score.toLong())
            )
        } else null
    }
}
