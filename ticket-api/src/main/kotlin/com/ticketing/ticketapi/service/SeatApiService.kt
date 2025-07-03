package com.ticketing.ticketapi.service

import com.ticketing.ticketapi.dto.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
@Service
class SeatApiService {
    fun reserveSeat(request: SeatReservationRequest): SeatReservationResponse {
        return SeatReservationResponse(
            success = true,
            message = "좌석 예약이 성공적으로 완료되었습니다.",
            reservationId = "RES_${UUID.randomUUID().toString().substring(0, 8)}",
            expiresAt = LocalDateTime.now().plusMinutes(10).toString()
        )
    }
    fun confirmReservation(request: ReservationConfirmRequest): ReservationConfirmResponse {
        return ReservationConfirmResponse(
            success = true,
            message = "예약이 확정되었습니다.",
            ticketId = "TICKET_${UUID.randomUUID().toString().substring(0, 8)}"
        )
    }
    fun getSeats(eventId: String): SeatListResponse {
        val seats = (1..20).map { seatNum ->
            SeatResponseDto(
                seatNumber = "A${seatNum.toString().padStart(2, '0')}",
                status = if (seatNum % 3 == 0) "RESERVED" else "AVAILABLE",
                price = 50000L,
                section = "A",
                row = "1",
                reservedBy = if (seatNum % 3 == 0) 1001L else null,
                expiresAt = if (seatNum % 3 == 0) LocalDateTime.now().plusMinutes(10).toString() else null
            )
        }
        
        return SeatListResponse(
            eventId = eventId,
            seats = seats
        )
    }
    fun getSeatStatus(eventId: String, seatNumber: String): SeatStatusResponse {
        return SeatStatusResponse(
            eventId = eventId,
            seatNumber = seatNumber,
            status = "AVAILABLE",
            reservedBy = null,
            expiresAt = null
        )
    }
    fun getUserReservations(userId: Long): UserReservationResponse {
        val reservations = listOf(
            ReservationInfo(
                reservationId = "RES_12345678",
                eventId = "EVENT_001",
                seatNumber = "A01",
                status = "CONFIRMED",
                createdAt = LocalDateTime.now().minusHours(1).toString(),
                expiresAt = null
            )
        )
        
        return UserReservationResponse(
            userId = userId,
            reservations = reservations
        )
    }
}
