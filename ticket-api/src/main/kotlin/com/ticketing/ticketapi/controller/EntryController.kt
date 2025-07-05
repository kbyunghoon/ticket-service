package com.ticketing.ticketapi.controller

import com.ticketing.ticketcore.service.RequestMonitorService
import com.ticketing.ticketcore.service.QueueService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/entry")
class EntryController(
    private val queueService: QueueService,
    private val requestMonitorService: RequestMonitorService
) {

    @GetMapping
    fun entryPage(): String {
        return "entry"
    }

    @PostMapping("/join")
    fun joinQueue(@RequestParam userId: Long, redirectAttributes: RedirectAttributes): String {
        return try {
            println("🚀 [DEBUG] 사용자 $userId 입장 요청")
            
            val queueSize = queueService.getQueueSize()
            val isOverloaded = requestMonitorService.isOverloaded()
            
            println("🔍 [DEBUG] 현재 대기열 크기: $queueSize")
            println("🔍 [DEBUG] 시스템 과부하 상태: $isOverloaded")

            if (isOverloaded) {
                val rank = queueService.enterQueue(userId)
                if (rank != null) {
                    println("[DEBUG] 사용자 $userId 대기열 입장 성공, 순위: $rank")
                    redirectAttributes.addAttribute("userId", userId)
                    "redirect:/entry/waiting"
                } else {
                    println("[DEBUG] 사용자 $userId 대기열 입장 실패")
                    redirectAttributes.addFlashAttribute("errorMessage", "대기열 입장에 실패했습니다.")
                    "redirect:/entry"
                }
            } else {
                val currentCount = requestMonitorService.incrementRequestCount()
                println("[DEBUG] 사용자 $userId 바로 입장 허용, 현재 요청 수: $currentCount")
                
                redirectAttributes.addAttribute("userId", userId)
                "redirect:/seat"
            }
        } catch (e: Exception) {
            println("[DEBUG] 오류 발생: ${e.message}")
            redirectAttributes.addFlashAttribute("errorMessage", "오류가 발생했습니다: ${e.message}")
            "redirect:/entry"
        }
    }

    @GetMapping("/waiting")
    fun waitingPage(@RequestParam userId: Long, model: Model): String {
        model.addAttribute("userId", userId)
        return "waiting"
    }

    @GetMapping("/api/rank/{userId}")
    @ResponseBody
    fun getUserRank(@PathVariable userId: Long): Map<String, Any?> {
        val rank = queueService.getQueueRank(userId)
        val queueSize = queueService.getQueueSize()

        return mapOf(
            "userId" to userId,
            "rank" to rank,
            "queueSize" to queueSize,
            "isAdmitted" to (rank == null),
            "timestamp" to System.currentTimeMillis()
        )
    }
}
