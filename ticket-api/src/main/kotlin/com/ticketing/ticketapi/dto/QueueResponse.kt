package com.ticketing.ticketapi.dto

data class QueueResponse(
    val userId: Long,
    val position: Long,
    val estimatedWaitTime: Long,
    val isActive: Boolean,
    val enteredAt: String
)
