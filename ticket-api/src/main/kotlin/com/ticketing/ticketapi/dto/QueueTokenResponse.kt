package com.ticketing.ticketapi.dto

import com.fasterxml.jackson.annotation.JsonProperty
data class QueueTokenResponse(
    @JsonProperty("token")
    val token: String,
    
    @JsonProperty("queuePosition")
    val queuePosition: Long,
    
    @JsonProperty("estimatedWaitTimeSeconds")
    val estimatedWaitTimeSeconds: Long,
    
    @JsonProperty("message")
    val message: String
)
