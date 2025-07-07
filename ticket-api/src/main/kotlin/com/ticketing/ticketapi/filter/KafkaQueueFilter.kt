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
            "/queue-test",
            "/entry"
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
        if (request.requestURI.startsWith("/seat")) {
            val token = request.getParameter("token")
            val userId = request.getParameter("userId")

            if (token.isNullOrBlank()) {
                println("토큰 없는 /seat 접근 - /entry로 리다이렉트: $requestId")
                response.sendRedirect("/entry")
                return
            }
            
            if (!isValidAccessToken(token, userId)) {
                println("무효한 토큰으로 /seat 접근 - /entry로 리다이렉트: $requestId, token: $token, userId: $userId")
                response.sendRedirect("/entry")
                return
            }
        }

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
        
        val isExcluded = EXCLUDED_PATHS.any { path.startsWith(it) }
        if (isExcluded) {
            println("필터 패스 - 제외 경로: $path")
            return true
        }

        if (path.startsWith("/seat")) {
            val token = request.getParameter("token")
            val userId = request.getParameter("userId")
            
            if (!token.isNullOrBlank() && isValidAccessToken(token, userId)) {
                println("필터 패스 - 유효한 토큰: $path, token: $token, userId: $userId")
                return true
            } else {
                println("필터 적용 - 토큰 없음/무효/userId 불일치: $path")
                return false
            }
        }
        
        println("필터 적용 - 경로: $path")
        return false
    }
    
    private fun isValidAccessToken(token: String, userId: String? = null): Boolean {
        if (!token.startsWith("ACCESS_TOKEN_")) {
            return false
        }

        if (userId != null) {
            try {
                val tokenParts = token.split("_")
                if (tokenParts.size >= 3) {
                    val tokenUserId = tokenParts[2]
                    if (tokenUserId != userId) {
                        println("토큰 검증 실패 - userId 불일치: token=$tokenUserId, param=$userId")
                        return false
                    }
                }
            } catch (e: Exception) {
                println("토큰 파싱 실패: $token")
                return false
            }
        }

        
        return true
    }

    private fun writeErrorResponse(response: HttpServletResponse, message: String) {
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val errorResponse = mapOf("error" to message, "timestamp" to LocalDateTime.now().toString())
        val errorJson = objectMapper.writeValueAsString(errorResponse)
        response.writer.write(errorJson)
        response.writer.flush()
    }
}
