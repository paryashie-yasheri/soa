package ru.ifmo.soa

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.ejb.Singleton
import jakarta.ejb.Startup
import java.net.HttpURLConnection
import java.net.URL

/** Registers the organization API with the local Consul agent during deployment. */
@Singleton
@Startup
class ConsulRegistration {
    private val consul = System.getProperty("soa.consul.url", "http://127.0.0.1:8500").trimEnd('/')
    private val serviceId = System.getProperty("soa.consul.service.id", "soa-organizations")

    @PostConstruct
    fun register() {
        val host = System.getProperty("soa.consul.service.address", "127.0.0.1")
        val port = System.getProperty("soa.consul.service.port", "61811").toInt()
        val json =
            """{"ID":"${escape(serviceId)}","Name":"soa-organizations","Address":"${escape(host)}","Port":$port,"Tags":["soa","organizations","lab3"]}"""
        var lastFailure: Exception? = null
        repeat(30) {
            try {
                request("PUT", "/v1/agent/service/register", json)
                return
            } catch (e: Exception) {
                lastFailure = e
                Thread.sleep(1_000)
            }
        }
        throw IllegalStateException("Could not register the organization service in Consul", lastFailure)
    }

    @PreDestroy
    fun deregister() {
        request("PUT", "/v1/agent/service/deregister/${encodePath(serviceId)}", "")
    }

    private fun request(method: String, path: String, body: String) {
        val connection = URL(consul + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            if (body.isNotEmpty()) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode in 200..299) {
                "Consul $method $path failed with HTTP ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun encodePath(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
