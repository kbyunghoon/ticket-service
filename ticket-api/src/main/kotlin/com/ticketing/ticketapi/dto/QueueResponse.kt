package com.ticketing.ticketapi.dto

data class QueueResponse(
    val userId: Long,
    val rank: Long?,
    val queueSize: Long,
    val message: String
)
