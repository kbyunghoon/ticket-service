package com.ticketing.ticketapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = ["com.ticketing.ticketapi", "com.ticketing.ticketcore"])
class TicketApiApplication

fun main(args: Array<String>) {
    runApplication<TicketApiApplication>(*args)
}
