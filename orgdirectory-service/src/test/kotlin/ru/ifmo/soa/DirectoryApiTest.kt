package ru.ifmo.soa

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

@QuarkusTest
@QuarkusTestResource(FakeOrganizationService::class)
class DirectoryApiTest {
    @Test
    fun `employee count filter calls the organization API and returns matching organizations`() {
        val response = given().get("/orgdirectory/filter/employees/1/1").then().statusCode(200).extract().asString()
        assertTrue(response.contains("<total>1</total>"))
        assertTrue(response.contains("<organization id=\"1\">"))
        assertTrue(!response.contains("id=\"2\""))
    }

    @Test
    fun `sort direction is passed to the organization API`() {
        val response = given().get("/orgdirectory/order/name/true").then().statusCode(200).extract().asString()
        assertTrue(response.indexOf("<organization id=\"2\">") < response.indexOf("<organization id=\"1\">") )
    }

    @Test
    fun `invalid employee range returns a client error`() {
        given().get("/orgdirectory/filter/employees/3/1").then().statusCode(400)
    }
}

class FakeOrganizationService : QuarkusTestResourceLifecycleManager {
    override fun start(): Map<String, String> {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/") { exchange -> respond(exchange) }
        server!!.start()
        return mapOf("organization-service.url" to "http://127.0.0.1:${server!!.address.port}")
    }

    override fun stop() { server?.stop(0); server = null }

    private fun respond(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val response = when {
            path == "/organizations" -> {
                val query = exchange.requestURI.rawQuery.orEmpty()
                val desc = query.split('&').any { it == "sortOrder=desc" }
                val ids = if (desc) listOf(2, 1) else listOf(1, 2)
                "<organizations><total>2</total><page>1</page><size>100</size>${ids.joinToString("") { orgXml(it) }}</organizations>"
            }
            path == "/organizations/1/employees" -> "<employees><employee id=\"10\" organizationId=\"1\"><name>A</name></employee></employees>"
            path == "/organizations/2/employees" -> "<employees><employee id=\"20\" organizationId=\"2\"><name>B</name></employee><employee id=\"21\" organizationId=\"2\"><name>C</name></employee></employees>"
            else -> "<error><code>404</code><message>missing</message></error>"
        }
        val status = if (path.startsWith("/organizations")) 200 else 404
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/xml")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun orgXml(id: Int) = "<organization id=\"$id\"><name>${if (id == 1) "Alpha" else "Beta"}</name><coordinates><x>$id</x><y>$id</y></coordinates><creationDate>2026-01-01</creationDate><type>COMMERCIAL</type><postalAddress><street>Street $id</street></postalAddress></organization>"

    companion object { @Volatile private var server: HttpServer? = null }
}
