package ru.ifmo.soa

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SoaApplication

fun main(args: Array<String>) {
    runApplication<SoaApplication>(*args)
}
