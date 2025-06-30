package com.ticketing.ticketcore.infra.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@EnableAutoConfiguration(exclude = [KafkaAutoConfiguration::class])
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RedisQueueManagerConcurrencyTest(
    private val redisQueueManager: RedisQueueManager,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @BeforeEach
    fun setUp() {
        redisQueueManager.clearQueue()
    }

    @Test
    fun `동일 사용자 동시 요청 시 중복 제거 및 후순위 이동 테스트`() {
        // Given
        val userId = 1L
        val concurrentRequests = 100
        val latch = CountDownLatch(concurrentRequests)
        val results = mutableListOf<Long?>()
        val executor = Executors.newFixedThreadPool(10)

        // When: 같은 사용자가 동시에 100번 요청
        repeat(concurrentRequests) {
            executor.submit {
                try {
                    val rank = redisQueueManager.addEntry(userId)
                    synchronized(results) {
                        results.add(rank)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        val queueSize = redisQueueManager.getQueueSize()
        val userRank = redisQueueManager.getRank(userId)
        val isInQueue = redisQueueManager.isUserInQueue(userId)

        // 대기열에 사용자가 단 한 번만 있어야 함
        assertThat(queueSize).isEqualTo(1)
        assertThat(userRank).isEqualTo(1)
        assertThat(isInQueue).isTrue()

        // 모든 요청이 성공적으로 처리되어야 함
        assertThat(results.size).isEqualTo(concurrentRequests)
        assertThat(results.all { it != null }).isTrue()
    }

    @Test
    fun `다중 사용자 동시 요청 시 순서 보장 테스트`() {
        // Given
        val userCount = 100
        val latch = CountDownLatch(userCount)
        val executor = Executors.newFixedThreadPool(20)
        val successCount = AtomicInteger(0)

        // When: 100명의 사용자가 동시에 요청
        repeat(userCount) { index ->
            executor.submit {
                try {
                    val userId = (index + 1).toLong()
                    val rank = redisQueueManager.addEntry(userId)
                    if (rank != null) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        val queueSize = redisQueueManager.getQueueSize()

        // 모든 사용자가 정확히 한 번씩만 대기열에 추가되어야 함
        assertThat(queueSize).isEqualTo(userCount.toLong())
        assertThat(successCount.get()).isEqualTo(userCount)

        // 순위가 연속적이어야 함
        val allUsers = (1L..userCount.toLong())
        allUsers.forEach { userId ->
            assertThat(redisQueueManager.isUserInQueue(userId)).isTrue()
            assertThat(redisQueueManager.getRank(userId)).isNotNull()
        }
    }

    @Test
    fun `동시 입장 및 처리 테스트`() {
        // Given
        val totalUsers = 50
        val admitCount = 10L
        val latch = CountDownLatch(totalUsers + 1)
        val executor = Executors.newFixedThreadPool(15)
        val addResults = mutableListOf<Long?>()
        val admitResults = mutableListOf<Long>()

        // When: 사용자 추가와 사용자 처리를 동시에 실행

        // 사용자 추가 (50명)
        repeat(totalUsers) { index ->
            executor.submit {
                try {
                    val userId = (index + 1).toLong()
                    val rank = redisQueueManager.addEntry(userId)
                    synchronized(addResults) {
                        addResults.add(rank)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // 잠시 후 사용자 처리
        executor.submit {
            try {
                Thread.sleep(100) // 일부 사용자가 추가될 때까지 대기
                val admitted = redisQueueManager.getAndRemoveTopEntries(admitCount)
                synchronized(admitResults) {
                    admitResults.addAll(admitted)
                }
            } finally {
                latch.countDown()
            }
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        val remainingQueueSize = redisQueueManager.getQueueSize()

        // 전체 사용자 수 = 처리된 사용자 수 + 남은 사용자 수
        assertThat(addResults.size).isEqualTo(totalUsers)
        assertThat(admitResults.size).isLessThanOrEqualTo(admitCount.toInt())
        assertThat(remainingQueueSize + admitResults.size).isEqualTo(totalUsers.toLong())

        // 처리된 사용자들은 대기열에 없어야 함
        admitResults.forEach { userId ->
            assertThat(redisQueueManager.isUserInQueue(userId)).isFalse()
        }
    }

    @Test
    fun `사용자 재요청 시 후순위 이동 동시성 테스트`() {
        // Given: 미리 10명의 사용자를 대기열에 추가
        repeat(10) { index ->
            redisQueueManager.addEntry((index + 1).toLong())
        }

        val targetUserId = 1L // 1순위 사용자
        val initialRank = redisQueueManager.getRank(targetUserId)

        val concurrentRequests = 50
        val latch = CountDownLatch(concurrentRequests)
        val executor = Executors.newFixedThreadPool(10)
        val results = mutableListOf<Long?>()

        // When: 1순위 사용자가 동시에 50번 재요청
        repeat(concurrentRequests) {
            executor.submit {
                try {
                    val rank = redisQueueManager.addEntry(targetUserId)
                    synchronized(results) {
                        results.add(rank)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        val finalRank = redisQueueManager.getRank(targetUserId)
        val queueSize = redisQueueManager.getQueueSize()

        // 사용자가 맨 뒤로 이동해야 함
        assertThat(initialRank).isEqualTo(1L)
        assertThat(finalRank).isEqualTo(10L) // 맨 뒤
        assertThat(queueSize).isEqualTo(10L) // 전체 사용자 수는 그대로

        // 모든 재요청이 성공해야 함
        assertThat(results.size).isEqualTo(concurrentRequests)
        assertThat(results.all { it != null }).isTrue()
    }

//    @Test
//    fun `대용량 동시 처리 스트레스 테스트`() {
//        // Given
//        val userCount = 1000
//        val batchSize = 100
//        val executor = Executors.newFixedThreadPool(50)
//        val futures = mutableListOf<CompletableFuture<Void>>()
//
//        // When: 1000명의 사용자가 동시에 요청
//        repeat(userCount) { index ->
//            val future = CompletableFuture.runAsync({
//                val userId = (index + 1).toLong()
//                redisQueueManager.addEntry(userId)
//            }, executor)
//            futures.add(future)
//        }
//
//        // 일부 사용자들을 배치로 처리
//        val processFuture = CompletableFuture.runAsync({
//            Thread.sleep(200) // 사용자 추가 대기
//            repeat(5) { // 5번에 걸쳐 배치 처리
//                redisQueueManager.getAndRemoveTopEntries(batchSize.toLong())
//                Thread.sleep(50)
//            }
//        }, executor)
//
//        futures.add(processFuture)
//        CompletableFuture.allOf(*futures.toTypedArray()).join()
//        executor.shutdown()
//
//        // Then
//        val remainingQueueSize = redisQueueManager.getQueueSize()
//
//        // 처리된 사용자를 제외하고 모든 사용자가 대기열에 있어야 함
//        assertThat(remainingQueueSize).isEqualTo((userCount - batchSize * 5).toLong())
//
//        // 남은 사용자들의 순위가 연속적이어야 함
//        (1L..remainingQueueSize).forEach { expectedRank ->
//            val usersAtRank = (1L..userCount.toLong()).count { userId ->
//                redisQueueManager.getRank(userId) == expectedRank
//            }
//            assertThat(usersAtRank).isEqualTo(1) // 각 순위에 정확히 한 명씩
//        }
//    }
}
