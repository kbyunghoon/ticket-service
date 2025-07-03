package com.ticketing.ticketapi.dto

data class UserReservationResponse(
    val userId: Long,
    val reservations: List<ReservationInfo>
)
