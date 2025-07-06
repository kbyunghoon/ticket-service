package com.ticketing.ticketcore.infra.kafka

import com.ticketing.ticketcommon.kafka.event.UserAdmissionEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class UserAdmissionProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(UserAdmissionProducer::class.java)

    @Value("\${app.kafka.topics.user-admitted:user-admitted-topic}")
    private lateinit var userAdmissionTopic: String

    fun sendUserAdmissionEvent(userId: Long, batchId: String? = null) {
        try {
            val event = UserAdmissionEvent.create(userId, batchId)

            kafkaTemplate.send(userAdmissionTopic, userId.toString(), event)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        logger.error("사용자 입장 이벤트 발송 실패 - userId: $userId, batchId: $batchId", ex)
                    } else {
                        logger.debug("사용자 입장 이벤트 발송 성공 - userId: $userId, offset: ${result?.recordMetadata?.offset()}")
                    }
                }

        } catch (e: Exception) {
            logger.error("사용자 입장 이벤트 생성 실패 - userId: $userId", e)
        }
    }

    fun sendBatchUserAdmissionEvents(userIds: List<Long>, batchId: String) {
        logger.info("배치 사용자 입장 이벤트 발송 시작 - batchId: $batchId, 사용자 수: ${userIds.size}")

        userIds.forEach { userId ->
            sendUserAdmissionEvent(userId, batchId)
        }

        logger.info("배치 사용자 입장 이벤트 발송 완료 - batchId: $batchId")
    }
}
