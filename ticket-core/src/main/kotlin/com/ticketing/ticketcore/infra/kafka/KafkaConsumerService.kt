package com.ticketing.ticketcore.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.ticketing.ticketcommon.dto.QueueRequestMessage
import com.ticketing.ticketcore.service.RequestMonitorService
import com.ticketing.ticketcore.service.RequestProcessingService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class KafkaConsumerService(
    private val requestMonitorService: RequestMonitorService,
    private val requestProcessingService: RequestProcessingService,
    private val objectMapper: ObjectMapper,
    @Qualifier("dlqKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    @Value("\${app.queue.kafka.dlq-topic:ticket-requests-dlq}")
    private val dlqTopic: String = "ticket-requests-dlq"

    @KafkaListener(
        topics = ["\${app.queue.kafka.topic:ticket-requests}"],
        groupId = "\${spring.kafka.consumer.group-id:ticket-queue-group}"
    )
    fun consumeQueueMessage(
        @Payload message: QueueRequestMessage,
        @Header("kafka_receivedMessageKey") token: String,
        acknowledgment: org.springframework.kafka.support.Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        var shouldCommit = false
        var retryCount = 0
        val maxRetries = 3

        try {
            println("[DEBUG] 메시지 수신 시간: ${LocalDateTime.now()}")
            println("[DEBUG] 스레드: ${Thread.currentThread().name}")
            println("[DEBUG] Token: $token, RequestID: ${message.requestId}")
            println("[DEBUG] canProcessRequest(): ${canProcessRequest()}")

            var lastException: Exception? = null
            for (attempt in 1..maxRetries) {
                try {
                    println("[DEBUG] 재요청 실행 직전 시간: ${LocalDateTime.now()} (시도: $attempt/$maxRetries)")
                    executeRequeue(message, token)
                    shouldCommit = true
                    break
                } catch (e: Exception) {
                    lastException = e
                    retryCount = attempt
                    println("재요청 실패 (시도 $attempt/$maxRetries) - 토큰: $token, Error: ${e.message}")

                    if (attempt < maxRetries) {
                        val delayMs = attempt * 1000L
                        println("${delayMs}ms 후 재시도...")
                        Thread.sleep(delayMs)
                    }
                }
            }

            if (!shouldCommit && lastException != null) {
                val dlqSendSuccess = sendToDeadLetterQueueSafely(message, token, lastException, retryCount)
                shouldCommit = dlqSendSuccess

                if (dlqSendSuccess) {
                    println("DLQ 전송 완료 - 원본 메시지 커밋 진행: $token")
                } else {
                    println("DLQ 전송 실패 - 원본 메시지 커밋 안함 (재처리 대기): $token")
                }
            }

            val processingTime = System.currentTimeMillis() - startTime
            if (shouldCommit) {
                println("메시지 처리 완료 및 커밋 - 토큰: $token (처리시간: ${processingTime}ms, 재시도: $retryCount)")
            }

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            println("메시지 처리 중 오류 발생 - 토큰: $token, 오류내용: ${e.message} (처리시간: ${processingTime}ms)")

            shouldCommit = false
        } finally {
            if (shouldCommit) {
                val commitStartTime = System.currentTimeMillis()
                acknowledgment.acknowledge()
                val commitTime = System.currentTimeMillis() - commitStartTime
                println("메시지 커밋 완료 - 토큰: $token (커밋시간: ${commitTime}ms)")
                println("[DEBUG] 전체 완료 시간: ${LocalDateTime.now()}")
                println("=".repeat(50))
            } else {
                println("메시지 커밋 안함 - 재처리 대기: $token")
            }
        }
    }

    private fun canProcessRequest(): Boolean {
        return !requestMonitorService.isOverloaded()
    }

    private fun executeRequeue(message: QueueRequestMessage, token: String) {
        try {
            val httpStartTime = System.currentTimeMillis()
            println("재요청 실행 (직접 호출) - ${message.method} ${message.endpoint}")

            val result = requestProcessingService.processQueuedRequest(message, token)

            val httpEndTime = System.currentTimeMillis()
            val httpDuration = httpEndTime - httpStartTime

            if (result.success) {
                println("재요청 성공 (직접 호출) - 토큰: $token, Status: ${result.statusCode}, 처리시간: ${httpDuration}ms")
            } else {
                println("재요청 실패 (직접 호출) - 토큰: $token, Status: ${result.statusCode}, Error: ${result.errorMessage}")
                throw RuntimeException("다이렉트 서비스 호출 실패: ${result.errorMessage}")
            }

        } catch (e: Exception) {
            println("재요청 실패 - 토큰: $token, Error: ${e.message}")
            throw e
        } finally {
            val remainingCount = requestMonitorService.decrementRequestCount()
            println("Kafka 재요청 완료 - Token: $token, 남은 요청 수: $remainingCount")
        }
    }

    fun getConsumerStatistics(): Map<String, Any> {
        return mapOf(
            "currentRequestCount" to requestMonitorService.getCurrentRequestCount(),
            "isOverloaded" to requestMonitorService.isOverloaded(),
            "timestamp" to System.currentTimeMillis()
        )
    }

    private fun sendToDeadLetterQueueSafely(
        message: QueueRequestMessage,
        token: String,
        lastException: Exception,
        retryCount: Int
    ): Boolean {
        return try {
            println("DLQ로 메시지 동기 전송 시작 - 토큰: $token, 최종 실패 이유: ${lastException.message}")

            val dlqMessage = createDlqMessage(message, token, lastException, retryCount)

            val sendResult = kafkaTemplate.send(dlqTopic, token, dlqMessage).get()

            val recordMetadata = sendResult.recordMetadata
            println("DLQ 전송 성공 - 토큰: $token, 토픽: ${recordMetadata.topic()}, 파티션: ${recordMetadata.partition()}, 오프셋: ${recordMetadata.offset()}")

            true

        } catch (e: Exception) {
            println("DLQ 전송 실패 - 토큰: $token, 에러: ${e.message}")

            handleDlqSendFailure(message, token, lastException, retryCount, e)

            false
        }
    }

    private fun createDlqMessage(
        originalMessage: QueueRequestMessage,
        token: String,
        lastException: Exception,
        retryCount: Int
    ): Map<String, Any> {
        return mapOf(
            "originalMessage" to originalMessage,
            "failureInfo" to mapOf(
                "token" to token,
                "failureReason" to (lastException.message ?: "Unknown error"),
                "exceptionType" to lastException.javaClass.simpleName,
                "stackTrace" to lastException.stackTraceToString(),
                "retryCount" to retryCount,
                "maxRetries" to 3,
                "failureTimestamp" to LocalDateTime.now(),
                "processingAttempts" to retryCount
            ),
            "metadata" to mapOf(
                "originalTopic" to "ticket-requests",
                "dlqVersion" to "1.0",
                "serviceName" to "ticket-queue-service",
                "environment" to System.getProperty("spring.profiles.active", "default")
            )
        )
    }

    private fun handleDlqSendFailure(
        originalMessage: QueueRequestMessage,
        token: String,
        originalException: Exception,
        retryCount: Int,
        dlqException: Throwable
    ) {
        try {

            val fallbackMessage = mapOf(
                "timestamp" to LocalDateTime.now(),
                "token" to token,
                "originalMessage" to originalMessage,
                "originalError" to originalException.message,
                "dlqError" to dlqException.message,
                "retryCount" to retryCount
            )

            val fallbackJson = objectMapper.writeValueAsString(fallbackMessage)
            println("DLQ 실패 - 로컬 백업 저장: $fallbackJson")
            println("DLQ 전송 실패 알림 필요 - 토큰: $token")

        } catch (e: Exception) {
            println("DLQ 대안 처리도 실패 - 토큰: $token, 에러: ${e.message}")
        }
    }
}
