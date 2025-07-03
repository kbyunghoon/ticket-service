package com.ticketing.ticketapi.dto

data class SeatReservationRequest(
    val eventId: String,
    val seatNumber: String,
    val userId: Long
)
