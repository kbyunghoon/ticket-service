package com.ticketing.ticketapi.controller

import com.ticketing.ticketapi.dto.QueueRequestMessage
import com.ticketing.ticketapi.service.KafkaProducerService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/debug")
class DebugController(
    private val kafkaProducerService: KafkaProducerService
) {

    @PostMapping("/send-test-messages/{count}")
    fun sendTestMessages(@PathVariable count: Int): Map<String, Any> {
        val startTime = System.currentTimeMillis()
        val messages = mutableListOf<String>()

        repeat(count) { i ->
            val message = QueueRequestMessage(
                requestId = "debug-$i-${System.currentTimeMillis()}",
                userId = "debug-user-$i",
                endpoint = "/api/queue/kafka/test-request",
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"testData": "message-$i"}""",
                queryParams = emptyMap(),
                timestamp = LocalDateTime.now(),
            )

            val response = kafkaProducerService.enqueueRequest(message)
            messages.add("Token: ${response.token}")
            println("[DEBUG] 메시지 $i 전송 완료: ${response.token}")
        }

        val endTime = System.currentTimeMillis()

        return mapOf(
            "totalMessages" to count,
            "sendingTimeMs" to (endTime - startTime),
            "tokens" to messages,
            "timestamp" to LocalDateTime.now()
        )
    }
}
