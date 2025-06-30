package com.ticketing.ticketcore.service

import com.ticketing.ticketcore.infra.redis.RedisQueueManager
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class QueueServiceDistributedLockTest {

    @Autowired
    private lateinit var queueService: QueueService

    @Autowired
    private lateinit var redisQueueManager: RedisQueueManager

    private val logger = LoggerFactory.getLogger(QueueServiceDistributedLockTest::class.java)

    @Test
    fun `분산락이 동시성을 제어하는지 테스트`() {
        // Given: 대기열에 20명 추가
        redisQueueManager.clearQueue()
        repeat(20) { i ->
            redisQueueManager.addEntry((i + 1).toLong())
        }

        val threadCount = 4
        val executor = Executors.newFixedThreadPool(threadCount)
        val countDownLatch = CountDownLatch(threadCount)
        val results = mutableListOf<List<Long>>()

        // When: 동시에 여러 스레드에서 admitUsers 호출
        val futures = (1..threadCount).map { threadNum ->
            CompletableFuture.supplyAsync {
                try {
                    countDownLatch.countDown()
                    countDownLatch.await() // 모든 스레드가 동시에 시작하도록
                    
                    val result = queueService.admitUsers(5)
                    logger.info("Thread $threadNum admitted: $result")
                    
                    synchronized(results) {
                        results.add(result)
                    }
                    
                    result
                } catch (e: Exception) {
                    logger.error("Thread $threadNum error", e)
                    emptyList<Long>()
                }
            }
        }

        // Then: 결과 검증
        val allResults = futures.map { it.get() }
        val totalAdmitted = allResults.flatten()
        
        logger.info("Total admitted users: ${totalAdmitted.size}")
        logger.info("Remaining queue size: ${queueService.getQueueSize()}")
        
        // 중복 없이 정확히 처리되었는지 확인
        val uniqueUsers = totalAdmitted.toSet()
        assertTrue(totalAdmitted.size == uniqueUsers.size, "분산락으로 중복 처리가 방지되어야 함")
        assertTrue(totalAdmitted.size <= 20, "전체 대기열 크기를 초과할 수 없음")
        
        executor.shutdown()
    }
}
