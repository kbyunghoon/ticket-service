package com.ticketing.ticketapi.dto

data class SeatResponseDto(
    val seatNumber: String,
    val status: String, // AVAILABLE, RESERVED, CONFIRMED, UNAVAILABLE
    val price: Long,
    val section: String,
    val row: String,
    val reservedBy: Long? = null,
    val expiresAt: String? = null
)
