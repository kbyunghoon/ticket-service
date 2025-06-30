package com.ticketing.ticketapi.controller

import com.ticketing.ticketapi.dto.AdmissionResponse
import com.ticketing.ticketapi.dto.QueueResponse
import com.ticketing.ticketapi.dto.QueueSizeResponse
import com.ticketing.ticketapi.service.QueueApiService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/queue")
class QueueController(
    private val queueApiService: QueueApiService
) {

    @PostMapping("/enter")
    fun enterQueue(@RequestParam(required = false) userId: Long?): ResponseEntity<QueueResponse> {
        val response = queueApiService.enterQueue(userId)

        return ResponseEntity.ok(response)
    }

    @GetMapping("/status")
    fun getQueueStatus(@RequestParam userId: Long): ResponseEntity<QueueResponse> {
        val response = queueApiService.getQueueStatus(userId)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/admit")
    fun admitUsers(@RequestParam(defaultValue = "1") count: Long): ResponseEntity<AdmissionResponse> {
        val response = queueApiService.admitUsers(count)

        return ResponseEntity.ok(response)
    }

    @GetMapping("/size")
    fun getQueueSize(): ResponseEntity<QueueSizeResponse> {
        val response = queueApiService.getQueueSize()

        return ResponseEntity.ok(response)
    }
}