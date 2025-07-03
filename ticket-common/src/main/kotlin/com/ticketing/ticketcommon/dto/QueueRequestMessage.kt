package com.ticketing.ticketcommon.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class QueueRequestMessage(
    @JsonProperty("requestId")
    val requestId: String,

    @JsonProperty("userId")
    val userId: String,

    @JsonProperty("endpoint")
    val endpoint: String,

    @JsonProperty("method")
    val method: String,

    @JsonProperty("headers")
    val headers: Map<String, String>,

    @JsonProperty("body")
    val body: String?,

    @JsonProperty("queryParams")
    val queryParams: Map<String, String>,

    @JsonProperty("timestamp")
    val timestamp: LocalDateTime,
)
