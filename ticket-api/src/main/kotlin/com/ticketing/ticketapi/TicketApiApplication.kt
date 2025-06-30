package com.ticketing.ticketapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.ticketing.ticketapi", "com.ticketing.ticketcore"])
class TicketApiApplication

fun main(args: Array<String>) {
    runApplication<TicketApiApplication>(*args)
}
