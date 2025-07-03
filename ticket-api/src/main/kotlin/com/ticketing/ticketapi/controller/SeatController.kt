package com.ticketing.ticketapi.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/seat")
class SeatController {

    @GetMapping
    fun seatPage(@RequestParam(required = false) userId: Long?, model: Model): String {
        model.addAttribute("userId", userId ?: 0)
        return "seat"
    }
}
