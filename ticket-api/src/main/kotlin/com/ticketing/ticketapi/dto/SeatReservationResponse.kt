package com.ticketing.ticketapi.dto

data class SeatReservationResponse(
    val success: Boolean,
    val message: String,
    val reservationId: String?,
    val expiresAt: String?
)
