package com.ticketing.ticketcore.service

import com.ticketing.ticketcommon.dto.QueueRequestMessage
import org.springframework.stereotype.Service

@Service
class RequestProcessingService {

    fun processQueuedRequest(message: QueueRequestMessage, token: String): RequestProcessingResult {
        return try {
            val startTime = System.currentTimeMillis()

            // TODO: 메세지 엔드포인트에 따라 서비스 메소드를 호출
            // 예: 
            // when (message.endpoint) {
            //     "/api/tickets/book" -> ticketBookingService.bookTicket(...)
            //     "/api/tickets/cancel" -> ticketCancelService.cancelTicket(...)
            //     else -> throw UnsupportedOperationException("Unsupported endpoint: ${message.endpoint}")
            // }

            // 현재는 성공으로 처리 (TODO: 이후 비즈니스 로직으로 교체 )
            simulateBusinessLogic(message)

            val endTime = System.currentTimeMillis()

            RequestProcessingResult(
                success = true,
                statusCode = 200,
                responseBody = """{"status": "success", "message": "Request processed directly", "requestId": "${message.requestId}"}""",
                headers = mapOf("Content-Type" to "application/json"),
                processingTimeMs = endTime - startTime
            )

        } catch (e: Exception) {
            RequestProcessingResult(
                success = false,
                statusCode = 500,
                responseBody = """{"error": "Internal server error", "message": "${e.message}"}""",
                headers = mapOf("Content-Type" to "application/json"),
                processingTimeMs = 0L,
                errorMessage = e.message
            )
        }
    }

    private fun simulateBusinessLogic(message: QueueRequestMessage) {
        if (message.endpoint.contains("error")) {
            throw RuntimeException("비즈니스 로직 오류")
        }
    }
}

data class RequestProcessingResult(
    val success: Boolean,
    val statusCode: Int,
    val responseBody: String,
    val headers: Map<String, String>,
    val processingTimeMs: Long,
    val errorMessage: String? = null
)
