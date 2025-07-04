package com.ticketing.ticketcore.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.ticketing.ticketcommon.dto.QueueRequestMessage
import com.ticketing.ticketcore.service.RequestMonitorService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

@Service
class KafkaConsumerService(
    private val requestMonitorService: RequestMonitorService,
    private val objectMapper: ObjectMapper
) {

    @Value("\${server.port:8080}")
    private val serverPort: Int = 8080

    private val restTemplate = RestTemplate().apply {
        val factory = org.springframework.http.client.SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(1000)
        factory.setReadTimeout(3000)
        requestFactory = factory
    }

    companion object {
        private const val REQUEUE_TOKEN_HEADER = "X-Requeue-Token"
        private const val CONTENT_TYPE_HEADER = "Content-Type"
        private const val BASE_URL = "http://localhost"
    }

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
        try {
            println("[DEBUG] 메시지 수신 시간: ${LocalDateTime.now()}")
            println("[DEBUG] 스레드: ${Thread.currentThread().name}")
            println("[DEBUG] Token: $token, RequestID: ${message.requestId}")
            println("[DEBUG] canProcessRequest(): ${canProcessRequest()}")

            println("[DEBUG] 재요청 실행 직전 시간: ${LocalDateTime.now()}")
            executeRequeue(message, token)
            shouldCommit = true

            val processingTime = System.currentTimeMillis() - startTime
            println("✅ 메시지 처리 완료 및 커밋 - 토큰: $token (처리시간: ${processingTime}ms)")

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            println("❌ 메시지 처리 실패 - Token: $token, Error: ${e.message} (처리시간: ${processingTime}ms)")
            shouldCommit = true
        } finally {
            if (shouldCommit) {
                val commitStartTime = System.currentTimeMillis()
                acknowledgment.acknowledge()
                val commitTime = System.currentTimeMillis() - commitStartTime
                println("메시지 커밋 완료 - 토큰: $token (커밋시간: ${commitTime}ms)")
                println("[DEBUG] 전체 완료 시간: ${LocalDateTime.now()}")
                println("=".repeat(50))
            }
        }
    }

    private fun canProcessRequest(): Boolean {
        return !requestMonitorService.isOverloaded()
    }

    private fun executeRequeue(message: QueueRequestMessage, token: String) {
        val url = buildRequestUrl(message)
        val headers = buildRequestHeaders(message, token)
        val httpMethod = HttpMethod.valueOf(message.method.uppercase())
        val entity = HttpEntity(message.body, headers)

        try {
            val httpStartTime = System.currentTimeMillis()
            println("재요청 실행 - ${message.method} $url")
            val response: ResponseEntity<String> = restTemplate.exchange(
                url,
                httpMethod,
                entity,
                String::class.java
            )

            val httpEndTime = System.currentTimeMillis()
            val httpDuration = httpEndTime - httpStartTime
            println("재요청 성공 - 토큰: $token, Status: ${response.statusCode}, HTTP시간: ${httpDuration}ms")

        } catch (e: Exception) {
            println("재요청 실패 - 토큰: $token, Error: ${e.message}")
            throw e
        } finally {
            val remainingCount = requestMonitorService.decrementRequestCount()
            println("Kafka 재요청 완료 - Token: $token, 남은 요청 수: $remainingCount")
        }
    }

    private fun buildRequestUrl(message: QueueRequestMessage): String {
        val baseUrl = "$BASE_URL:$serverPort${message.endpoint}"

        return if (message.queryParams.isNotEmpty()) {
            val queryString = message.queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            "$baseUrl?$queryString"
        } else {
            baseUrl
        }
    }

    private fun buildRequestHeaders(message: QueueRequestMessage, token: String): HttpHeaders {
        val headers = HttpHeaders()
        message.headers.forEach { (key, value) ->
            when (key.lowercase()) {
                "host", "content-length", "connection" -> {}

                else -> headers.add(key, value)
            }
        }
        headers.add(REQUEUE_TOKEN_HEADER, token)
        if (message.body != null && !headers.containsKey(CONTENT_TYPE_HEADER)) {
            headers.add(CONTENT_TYPE_HEADER, MediaType.APPLICATION_JSON_VALUE)
        }

        return headers
    }

    fun getConsumerStatistics(): Map<String, Any> {
        return mapOf(
            "currentRequestCount" to requestMonitorService.getCurrentRequestCount(),
            "isOverloaded" to requestMonitorService.isOverloaded(),
            "timestamp" to System.currentTimeMillis()
        )
    }
}
