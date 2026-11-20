package ru.ifmo.soa

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun health(): String = "OK"
}
