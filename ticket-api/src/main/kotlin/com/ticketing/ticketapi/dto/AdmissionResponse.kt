package com.ticketing.ticketapi.dto

data class AdmissionResponse(
    val admittedUsers: List<Long>,
    val admittedCount: Int,
    val remainingQueueSize: Long,
    val message: String
)
