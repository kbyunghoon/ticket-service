package com.ticketing.ticketapi.controller

import com.ticketing.ticketcore.service.QueueService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/debug/queue")
class QueueDebugController(
    private val queueService: QueueService,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @GetMapping("/current-state")
    fun getCurrentQueueState(): Map<String, Any> {
        val queueKey = "waiting_queue"
        val queueList = redisTemplate.opsForList().range(queueKey, 0, -1)
        val queueSize = redisTemplate.opsForList().size(queueKey) ?: 0

        return mapOf(
            "queueSize" to queueSize,
            "totalItems" to (queueList?.size ?: 0),
            "firstTen" to (queueList?.take(10) ?: emptyList()),
            "lastTen" to (queueList?.takeLast(10) ?: emptyList())
        )
    }

    @PostMapping("/clear")
    fun clearQueue(): Map<String, String> {
        redisTemplate.delete("waiting_queue")
        return mapOf("message" to "대기열 초기화 완료")
    }

    @PostMapping("/reset-and-test")
    fun resetAndTest(): Map<String, Any> {
        redisTemplate.delete("waiting_queue")
        val results = mutableListOf<Map<String, Any>>()
        for (i in 1..5) {
            val rank = queueService.enterQueue(i.toLong())
            results.add(
                mapOf(
                    "userId" to i,
                    "rank" to (rank ?: "null")
                )
            )
        }
        val finalState = getCurrentQueueState()

        return mapOf(
            "message" to "리셋 및 테스트 완료",
            "addResults" to results,
            "finalState" to finalState
        )
    }
}
