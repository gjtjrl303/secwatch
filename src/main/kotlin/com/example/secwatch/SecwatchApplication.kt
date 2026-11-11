package com.example.secwatch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SecwatchApplication

fun main(args: Array<String>) {
    runApplication<SecwatchApplication>(*args)
}
