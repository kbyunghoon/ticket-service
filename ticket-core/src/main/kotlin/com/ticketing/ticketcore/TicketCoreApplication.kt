package com.ticketing.ticketcore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TicketCoreApplication

fun main(args: Array<String>) {
    runApplication<TicketCoreApplication>(*args)
}
