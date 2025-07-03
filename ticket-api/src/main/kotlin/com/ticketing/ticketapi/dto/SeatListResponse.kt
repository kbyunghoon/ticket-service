package com.ticketing.ticketapi.dto

data class SeatListResponse(
    val eventId: String,
    val seats: List<SeatResponseDto>
)
