package com.ticketing.ticketcore.infra.kafka

import com.ticketing.ticketcommon.dto.QueueRequestMessage
import com.ticketing.ticketcommon.dto.QueueTokenResponse
import com.ticketing.ticketcore.service.RequestMonitorService
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class KafkaProducerService(
    private val kafkaTemplate: KafkaTemplate<String, QueueRequestMessage>,
    private val requestMonitorService: RequestMonitorService
) {

    @Value("\${app.queue.kafka.topic:ticket-requests}")
    private val topicName: String = "ticket-requests"
    
    fun enqueueRequest(queueMessage: QueueRequestMessage): QueueTokenResponse {
        val token = generateToken()
        val future: CompletableFuture<SendResult<String, QueueRequestMessage>> =
            kafkaTemplate.send(topicName, token, queueMessage)
        future.whenComplete { result, exception ->
            if (exception != null) {
                println("메시지 전송 실패: ${exception.message}")
            } else {
                val offset = result.recordMetadata.offset()
                println("메시지 전송 성공 - 토큰: $token, Offset: $offset")
            }
        }
        val queuePosition = System.currentTimeMillis() % 1000 + 1
        val estimatedWaitTime = requestMonitorService.getEstimatedWaitTime(queuePosition)

        return QueueTokenResponse(
            token = token,
            queuePosition = queuePosition,
            estimatedWaitTimeSeconds = estimatedWaitTime,
            message = "큐에 요청 추가 완료. 턴이 될 때 자동으로 진행될 예정"
        )
    }

    private fun generateToken(): String {
        return "TOKEN_${UUID.randomUUID().toString().replace("-", "").substring(0, 16)}"
    }
}
