package com.ticketing.ticketapi.controller

import com.ticketing.ticketcore.service.RequestMonitorService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/seat")
class SeatController(
    private val requestMonitorService: RequestMonitorService
) {

    @GetMapping
    fun seatPage(@RequestParam(required = false) userId: Long?, model: Model): String {
        model.addAttribute("userId", userId ?: 0)
        return "seat"
    }
    
    @PostMapping("/complete")
    fun completeProcessing(@RequestParam userId: Long, redirectAttributes: RedirectAttributes): String {
        return try {
            val currentCount = requestMonitorService.decrementRequestCount()
            println("[DEBUG] 사용자 $userId 처리 완료, 현재 요청 수: $currentCount")
            
            redirectAttributes.addFlashAttribute("successMessage", "처리가 완료되었습니다.")
            "redirect:/entry"
        } catch (e: Exception) {
            println("[DEBUG] 처리 완료 중 오류 발생: ${e.message}")
            redirectAttributes.addFlashAttribute("errorMessage", "처리 중 오류가 발생했습니다: ${e.message}")
            "redirect:/seat?userId=$userId"
        }
    }
}
