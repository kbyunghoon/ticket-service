package com.ticketing.ticketcore.infra.kafka

import com.ticketing.ticketcommon.kafka.event.UserAdmissionEvent
import com.ticketing.ticketcore.service.UserAdmissionService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class UserAdmissionConsumer(
    private val userAdmissionService: UserAdmissionService
) {
    private val logger = LoggerFactory.getLogger(UserAdmissionConsumer::class.java)

    @KafkaListener(
        topics = ["\${app.kafka.topics.user-admitted:user-admitted-topic}"],
        groupId = "\${spring.kafka.consumer.group-id:ticket-queue-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleUserAdmissionEvent(
        @Payload event: UserAdmissionEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        try {
            logger.info("사용자 입장 이벤트 수신 - userId: ${event.userId}, batchId: ${event.batchId}, partition: $partition, offset: $offset")

            userAdmissionService.processUserAdmission(event.userId)

            acknowledgment.acknowledge()

            logger.info("사용자 입장 처리 완료 - userId: ${event.userId}, batchId: ${event.batchId}")

        } catch (e: Exception) {
            logger.error("사용자 입장 처리 실패 - userId: ${event.userId}, batchId: ${event.batchId}", e)

            // 실패 시에도 acknowledge - DLQ나 재시도 로직은 별도 구현 필요
            // 프로덕션에서는 실패한 이벤트를 DLQ로 보내거나 재시도 로직 추가
            acknowledgment.acknowledge()
        }
    }
}
