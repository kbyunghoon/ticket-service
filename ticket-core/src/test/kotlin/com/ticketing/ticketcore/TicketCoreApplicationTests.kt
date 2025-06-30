package com.ticketing.ticketcore

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableAutoConfiguration(exclude = [KafkaAutoConfiguration::class])
@ActiveProfiles("test")
class TicketCoreApplicationTests {

    @Test
    fun contextLoads() {
    }

}
