package ru.ifmo.soa

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.w3c.dom.Element
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Exercises the WildFly deployment produced by `wildfly/Dockerfile`: both JAX-RS applications
 * behind a single TLS listener on port 61811, Swagger UI served at /ui, and the
 * shared PostgreSQL schema migrated by Liquibase.
 *
 * The stack itself is started and stopped by the `stackUp`/`stackDown` Gradle tasks that wrap
 * `wildfly/compose.yaml`; this suite only talks to the already running deployment.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WildFlyStackE2ETest {
    private val httpsPort = System.getenv("SOA_TEST_HTTPS_PORT") ?: "62811"
    private val api = "https://localhost:$httpsPort"
    private val directory = "$api/orgdirectory"
    private val swagger = "$api/ui"
    private val jdbcUrl = "jdbc:postgresql://localhost:15432/soa"
    private val dbUser = "soa"
    private val dbPassword = "soa-local"
    private lateinit var https: HttpClient

    @BeforeAll
    fun awaitDeployment() {
        https = HttpClient.newBuilder().sslContext(trustAll()).connectTimeout(Duration.ofSeconds(3)).build()
        await("$api/organizations")
        await("$directory/order/name/false")
    }

    @BeforeEach
    fun clearDatabase() {
        DriverManager.getConnection(jdbcUrl, dbUser, dbPassword).use { connection ->
            connection.createStatement().use {
                it.execute("TRUNCATE s389491.employees, s389491.organizations RESTART IDENTITY CASCADE")
            }
        }
    }

    @Test
    fun `organization CRUD stores records in the shared schema and preserves generated fields`() {
        val created = request("POST", "$api/organizations", organization("Acme", 100), 201)
        val id = created.getAttribute("id")
        val date = created.text("creationDate")
        assertFalse(date.isBlank())
        assertEquals("Acme", request("GET", "$api/organizations/$id").text("name"))

        DriverManager.getConnection(jdbcUrl, dbUser, dbPassword).use { connection ->
            connection.prepareStatement("SELECT name FROM s389491.organizations WHERE id = ?").use { statement ->
                statement.setInt(1, id.toInt())
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Acme", rows.getString(1))
                }
            }
        }

        val updated = request("PUT", "$api/organizations/$id", organization("Updated", 200))
        assertEquals("Updated", updated.text("name"))
        assertEquals(date, updated.text("creationDate"))
        request("DELETE", "$api/organizations/$id", expected = 204)
        assertEquals("404", request("GET", "$api/organizations/$id", expected = 404).text("code"))
    }

    @Test
    fun `collection supports filters sorting and paging`() {
        create("Alpha", 30)
        create("Beta", 10)
        create("Gamma", 20)

        val filtered = request("GET", "$api/organizations?name=Beta&type=COMMERCIAL")
        assertEquals("1", filtered.text("total"))
        assertEquals(listOf("Beta"), filtered.organizations().map { it.text("name") })

        val middle = request("GET", "$api/organizations?sortBy=annualTurnover&sortOrder=asc&page=2&size=1")
        assertEquals("3", middle.text("total"))
        assertEquals("2", middle.text("page"))
        assertEquals(listOf("Gamma"), middle.organizations().map { it.text("name") })

        val descending = request("GET", "$api/organizations?sortBy=name&sortOrder=desc")
        assertEquals(listOf("Gamma", "Beta", "Alpha"), descending.organizations().map { it.text("name") })
    }

    @Test
    fun `employees and aggregation endpoints use the database records`() {
        val first = create("Same", 100)
        val second = create("Same", 200)

        val employee = request("POST", "$api/organizations/$first/employees", employee("Ada"), 201)
        assertEquals(1, request("GET", "$api/organizations/$first/employees").getElementsByTagName("employee").length)
        request("DELETE", "$api/organizations/$first/employees/${employee.getAttribute("id")}", expected = 204)

        val groups = request("GET", "$api/organizations/stats/grouped-by-name")
        assertEquals("Same", groups.text("name"))
        assertEquals("2", groups.text("count"))
        assertEquals("1", request("GET", "$api/organizations/stats/annual-turnover/less-than/150").text("count"))
        val unique = request("GET", "$api/organizations/stats/annual-turnover/unique").getElementsByTagName("value")
        assertEquals(setOf("100", "200"), (0 until unique.length).map { unique.item(it).textContent }.toSet())
        assertTrue(second.toInt() > first.toInt())
    }

    @Test
    fun `directory service filters and sorts through the real upstream over TLS`() {
        val alpha = create("Alpha", 100)
        val beta = create("Beta", 200)
        request("POST", "$api/organizations/$alpha/employees", employee("Ada"), 201)
        request("POST", "$api/organizations/$beta/employees", employee("Grace"), 201)
        request("POST", "$api/organizations/$beta/employees", employee("Linus"), 201)

        val matches = request("GET", "$directory/filter/employees/1/1")
        assertEquals("1", matches.text("total"))
        assertEquals(listOf(alpha), matches.organizations().map { it.getAttribute("id") })

        val sorted = request("GET", "$directory/order/name/true")
        assertEquals(listOf("Beta", "Alpha"), sorted.organizations().map { it.text("name") })
    }

    @Test
    fun `validation errors from each service keep their status and message`() {
        val bad = request("POST", "$api/organizations", organization("", 100), 400)
        assertEquals("400", bad.text("code"))
        assertTrue(bad.text("message").contains("name"))
        request("GET", "$api/organizations?page=0", expected = 400)
        request("GET", "$api/organizations?creationDate=not-a-date", expected = 400)
        request("GET", "$api/organizations?sortBy=unknown", expected = 400)

        val range = request("GET", "$directory/filter/employees/2/1", expected = 400)
        assertEquals("400", range.text("code"))
        assertFalse(range.text("message").isBlank())
        request("GET", "$directory/order/unknown/false", expected = 400)
        request("GET", "$api/organizations/999999/employees", expected = 404)
    }

    @Test
    fun `Swagger UI and live OpenAPI documents are served and the API answers CORS requests`() {
        listOf("/", "/swagger-ui-bundle.js", "/swagger-ui.css").forEach { path ->
            val response = https.send(
                HttpRequest.newBuilder(URI.create("$swagger$path")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, response.statusCode(), path)
            assertFalse(response.body().isBlank())
        }
        val index = https.send(
            HttpRequest.newBuilder(URI.create("$swagger/")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertTrue(index.body().contains("SwaggerUIBundle"))

        for (service in listOf("organizations", "orgdirectory")) {
            val document = https.send(
                HttpRequest.newBuilder(URI.create("$api/openapi/$service?format=JSON")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, document.statusCode())
            assertTrue(document.body().contains("\"openapi\""))
            assertTrue(document.body().contains("\"paths\""))
        }

        val simple = https.send(
            HttpRequest.newBuilder(URI.create("$api/organizations")).header("Origin", "https://se.ifmo.ru").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals("*", simple.headers().firstValue("access-control-allow-origin").orElse(null))

        val preflight = https.send(
            HttpRequest.newBuilder(URI.create("$api/organizations"))
                .header("Origin", "https://se.ifmo.ru")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, preflight.statusCode())
        assertEquals(listOf("*"), preflight.headers().allValues("access-control-allow-origin"))
        assertEquals("*", preflight.headers().firstValue("access-control-allow-origin").orElse(null))
    }

    @Test
    fun `plaintext HTTP cannot reach the applications`() {
        val plain = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        try {
            val response = plain.send(
                HttpRequest.newBuilder(URI.create("http://localhost:$httpsPort/organizations"))
                    .timeout(Duration.ofSeconds(3)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertTrue(response.statusCode() >= 400, "plaintext request was served with ${response.statusCode()}")
        } catch (_: IOException) {
            // A TLS-only listener closes the connection when it receives plaintext.
        }
    }

    @Test
    fun `nullable XML and unbounded strings round trip while nonfinite coordinates are rejected`() {
        val name = "A".repeat(300)
        val body = organization(name, 100)
            .replace("<organization>", """<organization xmlns:n="http://www.w3.org/2001/XMLSchema-instance">""")
            .replace("<annualTurnover>100</annualTurnover>", """<annualTurnover n:nil="true"/>""")
        val created = request("POST", "$api/organizations", body, 201)
        assertEquals(name, created.text("name"))
        assertEquals(0, created.getElementsByTagName("annualTurnover").length)
        val id = created.getAttribute("id")
        request("POST", "$api/organizations/$id/employees", employee(name), 201)
        for (value in listOf("NaN", "Infinity", "-Infinity")) {
            request("POST", "$api/organizations", organization("Bad", 100).replace("<x>1.5</x>", "<x>$value</x>"), 400)
            request("GET", "$api/organizations?coordinates.x=$value", expected = 400)
        }
        request("DELETE", "$api/organizations/$id", expected = 204)
    }

    @Test
    fun `directory collects multiple pages and enum sorting uses names`() {
        for (index in 0..100) create("Item%03d".format(index), index + 1)
        val result = request("GET", "$directory/order/name/false")
        assertEquals("101", result.text("total"))
        assertEquals(101, result.organizations().map { it.getAttribute("id") }.toSet().size)
        assertEquals("Item100", result.organizations().last().text("name"))

        request("POST", "$api/organizations", organization("Open", 1).replace("COMMERCIAL", "OPEN_JOINT_STOCK_COMPANY"), 201)
        request("POST", "$api/organizations", organization("Private", 1).replace("COMMERCIAL", "PRIVATE_LIMITED_COMPANY"), 201)
        request("POST", "$api/organizations", organization("Trust", 1).replace("COMMERCIAL", "TRUST"), 201)
        val sorted = request("GET", "$api/organizations?sortBy=type&sortOrder=desc&size=3")
        assertEquals(listOf("TRUST", "PRIVATE_LIMITED_COMPANY", "OPEN_JOINT_STOCK_COMPANY"), sorted.organizations().map { it.text("type") })
    }

    private fun create(name: String, turnover: Int): String =
        request("POST", "$api/organizations", organization(name, turnover), 201).getAttribute("id")

    private fun organization(name: String, turnover: Int) =
        "<organization><name>$name</name><coordinates><x>1.5</x><y>2</y></coordinates><annualTurnover>$turnover</annualTurnover><type>COMMERCIAL</type><postalAddress><street>Main street</street></postalAddress></organization>"

    private fun employee(name: String) = "<employee><name>$name</name><position>Engineer</position><salary>5000</salary></employee>"

    private fun request(method: String, url: String, body: String? = null, expected: Int = 200): Element {
        val response = send(method, url, body)
        assertEquals(expected, response.statusCode(), "$method $url: ${response.body()}")
        val xml = if (expected == 204) "<empty/>" else response.body()
        return DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun send(method: String, url: String, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("Accept", "application/xml")
        if (body != null) builder.header("Content-Type", "application/xml")
        return https.send(
            builder.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun await(url: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
        var lastFailure: String? = null
        while (System.nanoTime() < deadline) {
            try {
                val response = send("GET", url)
                if (response.statusCode() == 200) return
                lastFailure = "HTTP ${response.statusCode()}"
            } catch (e: Exception) {
                lastFailure = e.message
            }
            Thread.sleep(500)
        }
        error("$url did not become ready: $lastFailure")
    }

    private fun Element.text(tag: String): String = if (tagName == tag) textContent
        else getElementsByTagName(tag).item(0)?.textContent ?: error("Missing <$tag> in <$tagName>")

    private fun Element.organizations(): List<Element> = getElementsByTagName("organization").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun trustAll(): SSLContext {
        val manager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(manager), SecureRandom()) }
    }
}
