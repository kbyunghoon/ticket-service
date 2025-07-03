package com.ticketing.ticketapi.dto

data class AdmissionResponse(
    val admittedCount: Long,
    val remainingQueueSize: Long,
    val message: String
)
