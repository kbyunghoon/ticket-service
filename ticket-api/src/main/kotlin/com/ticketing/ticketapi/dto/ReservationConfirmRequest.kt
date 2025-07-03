package com.ticketing.ticketapi.dto

data class ReservationConfirmRequest(
    val reservationId: String,
    val userId: Long
)
