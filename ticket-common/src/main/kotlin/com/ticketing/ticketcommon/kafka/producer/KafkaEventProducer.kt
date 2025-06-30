package com.ticketing.ticketcommon.kafka.producer

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(KafkaTemplate::class)
class KafkaEventProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {
    fun sendGenericEvent(topic: String, key: String, event: Any) {
        kafkaTemplate.send(topic, key, event)
    }
}
