package com.ticketing.ticketapi.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.ticketing.ticketcommon.dto.QueueRequestMessage
import com.ticketing.ticketcore.infra.kafka.KafkaProducerService
import com.ticketing.ticketcore.service.RequestMonitorService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import java.time.LocalDateTime
import java.util.*

@Component
@Order(1)
class KafkaQueueFilter(
    private val requestMonitorService: RequestMonitorService,
    private val kafkaProducerService: KafkaProducerService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        private const val REQUEUE_TOKEN_HEADER = "X-Requeue-Token"
        private const val REQUEST_ID_HEADER = "X-Request-ID"
        private val EXCLUDED_PATHS = setOf(
            "/actuator",
            "/health",
            "/api/queue/kafka/status",
            "/api/queue/size",
            "/api/queue/kafka/test-request",
            "/queue-test"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestWrapper = ContentCachingRequestWrapper(request)
        val requestId = UUID.randomUUID().toString()

        try {
            if (shouldSkipFilter(request)) {
                filterChain.doFilter(requestWrapper, response)
                return
            }

            val requeueToken = request.getHeader(REQUEUE_TOKEN_HEADER)
            if (!requeueToken.isNullOrBlank()) {
                println("재요청 처리 - 토큰: $requeueToken")
                handleRequeueRequest(requestWrapper, response, filterChain, requestId)
                return
            }

            handleNormalRequest(requestWrapper, response, filterChain, requestId)

        } catch (e: Exception) {
            println("필터 처리 중 오류 발생: ${e.message}")
            response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            writeErrorResponse(response, "오류 발생")
        }
    }

    private fun handleNormalRequest(
        request: ContentCachingRequestWrapper,
        response: HttpServletResponse,
        filterChain: FilterChain,
        requestId: String
    ) {

        val currentRequestCount = requestMonitorService.incrementRequestCount()
        response.setHeader(REQUEST_ID_HEADER, requestId)
        var isQueuedRequest = false
        
        try {
            if (requestMonitorService.isOverloaded()) {
                println("과부하 상태 - 요청을 큐에 추가: $requestId")
                handleOverloadedRequest(request, response, requestId)
                isQueuedRequest = true
            } else {
                println("정상 요청 처리 - ID: $requestId, 현재 요청 수: $currentRequestCount")
                filterChain.doFilter(request, response)
            }
        } finally {
            // 큐에 보낸 요청은 여기서 카운터 감소하지 않음 (Kafka에서 처리 완료 시 감소)
            if (!isQueuedRequest) {
                val remainingCount = requestMonitorService.decrementRequestCount()
                println("요청 완료 - ID: $requestId, 남은 요청 수: $remainingCount")
            } else {
                println("큐 요청 - ID: $requestId, 카운터 유지 (Kafka 처리 시 감소 예정)")
            }
        }
    }

    private fun handleRequeueRequest(
        request: ContentCachingRequestWrapper,
        response: HttpServletResponse,
        filterChain: FilterChain,
        requestId: String
    ) {

        filterChain.doFilter(request, response)
        println("재요청 완료 - ID: $requestId (카운터 관리 없음)")
    }

    private fun handleOverloadedRequest(
        request: ContentCachingRequestWrapper,
        response: HttpServletResponse,
        requestId: String
    ) {
        val queueMessage = createQueueMessage(request, requestId)
        val tokenResponse = kafkaProducerService.enqueueRequest(queueMessage)

        response.status = HttpStatus.ACCEPTED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val responseJson = objectMapper.writeValueAsString(tokenResponse)
        response.writer.write(responseJson)
        response.writer.flush()

        println("대기열 순번 생성 - 토큰: ${tokenResponse.token}, 랭크: ${tokenResponse.queuePosition}")
    }

    private fun createQueueMessage(request: ContentCachingRequestWrapper, requestId: String): QueueRequestMessage {
        val headers = mutableMapOf<String, String>()
        request.headerNames.asIterator().forEach { headerName ->
            headers[headerName] = request.getHeader(headerName) ?: ""
        }

        val queryParams = mutableMapOf<String, String>()
        request.parameterMap.forEach { (key, values) ->
            queryParams[key] = values.joinToString(",")
        }

        val body = try {
            String(request.contentAsByteArray, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        return QueueRequestMessage(
            requestId = requestId,
            userId = extractUserId(request),
            endpoint = request.requestURI,
            method = request.method,
            headers = headers,
            body = body,
            queryParams = queryParams,
            timestamp = LocalDateTime.now(),
        )
    }

    private fun extractUserId(request: HttpServletRequest): String {
        return request.getHeader("X-User-ID")
            ?: request.getParameter("userId")
            ?: "anonymous"
    }

    private fun shouldSkipFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        val shouldSkip = EXCLUDED_PATHS.any { path.startsWith(it) }

        if (shouldSkip) {
            println("필터 패스 - 경로: $path")
        } else {
            println("필터 적용 - 경로: $path")
        }

        return shouldSkip
    }

    private fun writeErrorResponse(response: HttpServletResponse, message: String) {
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val errorResponse = mapOf("error" to message, "timestamp" to LocalDateTime.now().toString())
        val errorJson = objectMapper.writeValueAsString(errorResponse)
        response.writer.write(errorJson)
        response.writer.flush()
    }
}
