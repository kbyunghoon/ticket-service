package com.ticketing.ticketapi.dto

data class ReservationConfirmResponse(
    val success: Boolean,
    val message: String,
    val ticketId: String?
)
