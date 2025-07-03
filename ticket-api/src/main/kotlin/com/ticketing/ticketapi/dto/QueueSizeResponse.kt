package com.ticketing.ticketapi.dto

data class QueueSizeResponse(
    val totalSize: Long,
    val activeUsers: Long,
    val estimatedProcessingTime: Long
)
