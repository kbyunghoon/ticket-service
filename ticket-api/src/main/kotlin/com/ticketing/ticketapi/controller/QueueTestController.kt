package com.ticketing.ticketapi.controller

import com.ticketing.ticketcore.service.RequestMonitorService
import com.ticketing.ticketcore.service.QueueService
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/queue-test")
class QueueTestController(
    private val queueService: QueueService,
    private val requestMonitorService: RequestMonitorService
) {

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun queueTestPage(model: Model): String {
        val currentRequestCount = requestMonitorService.getCurrentRequestCount()
        val queueSize = queueService.getQueueSize()
        val isOverloadedWithQueue = requestMonitorService.isOverloadedIncludingQueue(queueSize)

        model.addAttribute("currentRequestCount", currentRequestCount)
        model.addAttribute("isOverloaded", isOverloadedWithQueue)
        model.addAttribute("queueSize", queueSize)

        return "queue-test"
    }

    @GetMapping("/simple", produces = [MediaType.TEXT_HTML_VALUE])
    fun simpleTestPage(model: Model): String {
        model.addAttribute("currentRequestCount", requestMonitorService.getCurrentRequestCount())
        model.addAttribute("isOverloaded", requestMonitorService.isOverloaded())
        model.addAttribute("queueSize", queueService.getQueueSize())

        return "simple-test"
    }

    @GetMapping("/api/status")
    @ResponseBody
    fun getStatus(): Map<String, Any> {
        val currentRequestCount = requestMonitorService.getCurrentRequestCount()
        val queueSize = queueService.getQueueSize()
        val isOverloaded = requestMonitorService.isOverloaded()
        val isOverloadedWithQueue = requestMonitorService.isOverloadedIncludingQueue(queueSize)

        return mapOf(
            "currentRequestCount" to currentRequestCount,
            "isOverloaded" to isOverloaded,
            "isOverloadedIncludingQueue" to isOverloadedWithQueue,
            "queueSize" to queueSize,
            "message" to "API is working!",
            "debug" to mapOf(
                "timestamp" to System.currentTimeMillis(),
                "threadName" to Thread.currentThread().name,
                "totalLoad" to (currentRequestCount + queueSize)
            )
        )
    }

    @GetMapping("/api/debug-overload")
    @ResponseBody
    fun debugOverload(): Map<String, Any> {
        val currentCount = requestMonitorService.getCurrentRequestCount()
        val queueSize = queueService.getQueueSize()
        val threshold = 10
        val totalLoad = currentCount + queueSize
        val shouldBeOverloaded = totalLoad >= threshold
        val actualResult = requestMonitorService.isOverloadedIncludingQueue(queueSize)

        return mapOf(
            "currentCount" to currentCount,
            "queueSize" to queueSize,
            "threshold" to threshold,
            "totalLoad" to totalLoad,
            "shouldBeOverloaded" to shouldBeOverloaded,
            "actualResult" to actualResult,
            "calculation" to "$totalLoad >= $threshold = $shouldBeOverloaded",
            "problem" to if (shouldBeOverloaded != actualResult) "MISMATCH" else "OK",
            "debug" to mapOf(
                "methodCalled" to "check console logs",
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    @GetMapping("/api/kafka-status")
    @ResponseBody
    fun getKafkaStatus(): Map<String, Any> {
        return mapOf(
            "message" to "Kafka Consumer 상태는 스프링부트 로그에서 확인 가능",
            "logMessages" to listOf(
                "🔍 [DEBUG] 메시지 수신 시간: ...",
                "🚀 재요청 실행 - POST ...",
                "✅ 재요청 성공 - Token: ...",
                "📊 재요청 완료 통계 - User: ..."
            ),
            "schedulerInfo" to mapOf(
                "interval" to "5초마다",
                "batchSize" to "10명씩",
                "lastCheck" to System.currentTimeMillis()
            )
        )
    }

    @PostMapping("/api/reset-counters")
    @ResponseBody
    fun resetCounters(): Map<String, Any> {
        return try {
            val beforeCount = requestMonitorService.getCurrentRequestCount()
            repeat(300) {
                requestMonitorService.decrementRequestCount()
            }

            val afterCount = requestMonitorService.getCurrentRequestCount()

            mapOf(
                "success" to true,
                "message" to "카운터 초기화 완료",
                "beforeCount" to beforeCount,
                "afterCount" to afterCount,
                "isOverloaded" to requestMonitorService.isOverloaded()
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "초기화 실패: ${e.message}"
            )
        }
    }

    @PostMapping("/enter")
    fun enterQueue(@RequestParam userId: Long, redirectAttributes: RedirectAttributes): String {
        return try {
            val rank = queueService.enterQueue(userId)
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "사용자 $userId 대기열 입장 완료. 순위: $rank"
            )
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "대기열 입장 실패: ${e.message}"
            )
        }.let { "redirect:/queue-test" }
    }

    @PostMapping("/check-rank")
    fun checkRank(@RequestParam userId: Long, redirectAttributes: RedirectAttributes): String {
        return try {
            val rank = queueService.getQueueRank(userId)
            if (rank != null) {
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "사용자 $userId 현재 순위: $rank"
                )
            } else {
                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "사용자 $userId 대기열에 없음"
                )
            }
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "순위 조회 실패: ${e.message}"
            )
        }.let { "redirect:/queue-test" }
    }

    @PostMapping("/admit")
    fun admitUsers(
        @RequestParam(defaultValue = "10") count: Long,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            val admittedUsers = queueService.admitUsers(count)
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "입장 처리 완료! ${admittedUsers.size}명 입장: $admittedUsers"
            )
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "입장 처리 실패: ${e.message}"
            )
        }.let { "redirect:/queue-test" }
    }

    @PostMapping("/clear")
    fun clearQueue(redirectAttributes: RedirectAttributes): String {
        return try {
            queueService.getQueueSize()
            repeat(200) {
                requestMonitorService.decrementRequestCount()
            }

            redirectAttributes.addFlashAttribute("successMessage", "대기열 및 요청 카운터 초기화 완료")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "초기화 실패: ${e.message}")
        }.let { "redirect:/queue-test" }
    }

    @PostMapping("/simulate-load")
    fun ㅌsimulateLoad(
        @RequestParam(defaultValue = "50") requestCount: Int,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            repeat(requestCount) {
                requestMonitorService.incrementRequestCount()
            }
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "부하 시뮬레이션 완료. $requestCount 개 요청 추가"
            )
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "부하 시뮬레이션 실패: ${e.message}")
        }.let { "redirect:/queue-test" }
    }
}
