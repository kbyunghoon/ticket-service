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

            val queueSize = queueService.getQueueSize()
            val isOverloaded = requestMonitorService.isOverloadedIncludingQueue(queueSize)

            if (isOverloaded) {
                val rank = queueService.enterQueue(userId)
                if (rank != null) {
                    redirectAttributes.addAttribute("userId", userId)
                    "redirect:/entry/waiting"
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "대기열 입장에 실패했습니다.")
                    "redirect:/entry"
                }
            } else {
                redirectAttributes.addAttribute("userId", userId)
                "redirect:/seat"
            }
        } catch (e: Exception) {
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
