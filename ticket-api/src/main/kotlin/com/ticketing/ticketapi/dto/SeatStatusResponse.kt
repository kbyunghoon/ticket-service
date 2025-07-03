package com.ticketing.ticketapi.dto

data class SeatStatusResponse(
    val eventId: String,
    val seatNumber: String,
    val status: String,
    val reservedBy: Long?,
    val expiresAt: String?
)
