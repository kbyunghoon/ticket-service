package com.ticketing.ticketcommon.kafka.event

import java.time.LocalDateTime

data class UserAdmissionEvent(
    val userId: Long,
    val admissionTime: LocalDateTime = LocalDateTime.now(),
    val eventId: String? = null,
    val batchId: String? = null
) {
    companion object {
        fun create(userId: Long, batchId: String? = null): UserAdmissionEvent {
            return UserAdmissionEvent(
                userId = userId,
                batchId = batchId
            )
        }
    }
}
