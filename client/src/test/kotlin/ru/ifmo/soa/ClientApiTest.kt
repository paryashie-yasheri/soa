package ru.ifmo.soa

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

@QuarkusTest
@QuarkusTestResource(FakeApiServices::class)
class ClientApiTest {
    @Test
    fun `client serves the browser application`() {
        given().get("/").then().statusCode(200).body(containsString("Каталог организаций"))
    }

    @Test
    fun `client proxies list create and service errors`() {
        given().get("/api/organizations?name=Acme").then().statusCode(200).body(containsString("<name>Acme</name>"))
        given().contentType("application/xml").body("<organization><name>New Co</name></organization>")
            .post("/api/organizations").then().statusCode(201).body(containsString("New Co"))
        given().get("/api/organizations/missing").then().statusCode(404).body(containsString("upstream missing"))
    }

    @Test
    fun `client routes directory calls to the second service`() {
        given().get("/api/directory/order/name/false").then().statusCode(200).body(containsString("Directory result"))
    }
}

class FakeApiServices : QuarkusTestResourceLifecycleManager {
    override fun start(): Map<String, String> {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/") { exchange -> respond(exchange) }
        server!!.start()
        val base = "http://127.0.0.1:${server!!.address.port}"
        return mapOf("organization-service.url" to base, "org-directory-service.url" to "$base/orgdirectory")
    }

    override fun stop() { server?.stop(0); server = null }

    private fun respond(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val status: Int
        val body: String
        when {
            path == "/organizations" && exchange.requestMethod == "POST" -> {
                val input = exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
                status = 201
                body = "<organization id=\"31\"><name>${if (input.contains("New Co")) "New Co" else "created"}</name></organization>"
            }
            path == "/organizations" -> {
                status = 200
                body = "<organizations><total>1</total><page>1</page><size>10</size><organization id=\"1\"><name>Acme</name></organization></organizations>"
            }
            path == "/organizations/missing" -> {
                status = 404
                body = "<error><code>404</code><message>upstream missing</message></error>"
            }
            path.startsWith("/orgdirectory/") -> {
                status = 200
                body = "<organizations><total>1</total><organization id=\"1\"><name>Directory result</name></organization></organizations>"
            }
            else -> {
                status = 404
                body = "<error><code>404</code><message>not found</message></error>"
            }
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/xml")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object { @Volatile private var server: HttpServer? = null }
}
