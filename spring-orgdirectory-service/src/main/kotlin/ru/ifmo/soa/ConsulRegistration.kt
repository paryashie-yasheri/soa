package ru.ifmo.soa

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
@ConditionalOnProperty(name = ["consul.registration.enabled"], havingValue = "true")
class ConsulRegistration(private val environment: Environment, private val json: ObjectMapper) {
    private val consul = environment.getProperty("CONSUL_HTTP_ADDR", "http://127.0.0.1:61818").trimEnd('/')
    private val port = environment.getRequiredProperty("server.port").toInt()
    private val serviceId = "soa-orgdirectory-$port"
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private var registered = false

    @EventListener(ApplicationReadyEvent::class)
    fun register() {
        val address = environment.getProperty("CONSUL_SERVICE_ADDRESS", "127.0.0.1")
        val body = json.writeValueAsString(mapOf(
            "ID" to serviceId,
            "Name" to "soa-orgdirectory",
            "Address" to address,
            "Port" to port,
            "Check" to mapOf(
                "HTTP" to "http://$address:$port/health",
                "Interval" to "5s",
                "Timeout" to "2s"
            )
        ))
        request("/v1/agent/service/register", body)
        registered = true
    }

    @PreDestroy
    fun deregister() {
        if (registered) request("/v1/agent/service/deregister/$serviceId", "")
    }

    private fun request(path: String, body: String) {
        val request = HttpRequest.newBuilder(URI.create(consul + path))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() in 200..299) { "Consul $path returned HTTP ${response.statusCode()}" }
    }
}
