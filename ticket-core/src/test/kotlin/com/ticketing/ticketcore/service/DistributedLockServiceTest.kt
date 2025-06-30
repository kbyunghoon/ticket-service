package com.ticketing.ticketcore.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class DistributedLockServiceTest {

    @Autowired
    private lateinit var distributedLockService: DistributedLockService

    private val logger = LoggerFactory.getLogger(DistributedLockServiceTest::class.java)

    @Test
    @DisplayName("분산락 기본 동작 테스트")
    fun `분산락이 정상적으로 동작해야 한다`() {
        // Given
        val lockKey = "test-lock-key"
        var executed = false

        // When
        val result = distributedLockService.executeWithLock(lockKey) {
            executed = true
            "success"
        }

        // Then
        assertTrue(result is LockResult.Success)
        assertEquals("success", (result as LockResult.Success).value)
        assertTrue(executed)
    }

    @Test
    @DisplayName("분산락 예외 처리 테스트")
    fun `락 실행 중 예외 발생 시 Error가 반환되어야 한다`() {
        // Given
        val lockKey = "exception-test-lock"

        // When: 락 실행 중 예외 발생
        val result = distributedLockService.executeWithLock(lockKey) {
            throw RuntimeException("테스트 예외")
        }

        // Then: Error 결과 반환
        assertTrue(result is LockResult.Error)
        assertTrue((result as LockResult.Error).message.contains("테스트 예외"))
    }

    @Test
    @DisplayName("대기열 작업 분산락 테스트")
    fun `대기열 작업에서 분산락이 올바르게 동작해야 한다`() {
        // Given
        val eventId = "test-event-123"
        val operation = "admit"
        var executed = false

        // When
        val result = distributedLockService.executeQueueOperation(eventId, operation) {
            executed = true
        }

        // Then
        assertTrue(result, "대기열 작업이 성공해야 함")
        assertTrue(executed, "작업이 실행되어야 함")
    }
}
