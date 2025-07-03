package com.ticketing.ticketapi.controller

import com.ticketing.ticketapi.dto.AdmissionResponse
import com.ticketing.ticketapi.dto.QueueResponse
import com.ticketing.ticketapi.dto.QueueSizeResponse
import com.ticketing.ticketapi.service.KafkaConsumerService
import com.ticketing.ticketapi.service.QueueApiService
import com.ticketing.ticketapi.service.RequestMonitorService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/queue")
class QueueController(
    private val queueApiService: QueueApiService,
    private val requestMonitorService: RequestMonitorService,
    private val kafkaConsumerService: KafkaConsumerService
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

    @GetMapping("/kafka/status")
    fun getKafkaQueueStatus(): ResponseEntity<Map<String, Any>> {
        val status = mapOf(
            "currentRequestCount" to requestMonitorService.getCurrentRequestCount(),
            "isOverloaded" to requestMonitorService.isOverloaded(),
            "consumerStats" to kafkaConsumerService.getConsumerStatistics(),
            "timestamp" to System.currentTimeMillis()
        )
        return ResponseEntity.ok(status)
    }

    @PostMapping("/kafka/test/overload")
    fun testOverloadState(@RequestParam enable: Boolean): ResponseEntity<Map<String, Any>> {
        repeat(if (enable) 150 else -150) {
            if (enable) requestMonitorService.incrementRequestCount()
            else requestMonitorService.decrementRequestCount()
        }

        val response = mapOf(
            "message" to "과부하 상태 ${if (enable) "활성화" else "비활성화"}",
            "currentRequestCount" to requestMonitorService.getCurrentRequestCount(),
            "isOverloaded" to requestMonitorService.isOverloaded()
        )

        return ResponseEntity.ok(response)
    }

    @PostMapping("/kafka/test-request")
    fun testKafkaQueue(): ResponseEntity<Map<String, Any>> {

        val response = mapOf(
            "message" to "Kafka 대기열 테스트 완료",
            "timestamp" to System.currentTimeMillis(),
            "processed" to true
        )
        return ResponseEntity.ok(response)
    }
}