package com.ticketing.ticketapi.dto

data class ReservationInfo(
    val reservationId: String,
    val eventId: String,
    val seatNumber: String,
    val status: String,
    val createdAt: String,
    val expiresAt: String?
)
