package ru.ifmo.soa

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.w3c.dom.Element
import java.io.IOException
import java.io.StringReader
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Runs packaged services with TLS, PostgreSQL and real upstream calls. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StackE2ETest {
    private val root = Path.of(System.getProperty("soa.root"))
    private val work = Path.of(System.getProperty("soa.e2e.work"))
    private val database = TestPostgres().apply {
        withDatabaseName("soa")
        withUsername("soa")
        withPassword("soa-e2e")
        withInitScript("db/schema.sql")
    }
    private val processes = linkedMapOf<String, Process>()
    private val ports = linkedMapOf<String, Int>()
    private lateinit var http: HttpClient
    private val keystore get() = work.resolve("server.p12")

    @BeforeAll
    fun startStack() {
        Files.createDirectories(work)
        try {
            createCertificate()
            val store = KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(keystore).use { load(it, "changeit".toCharArray()) }
            }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
            http = HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(2)).build()
            database.start()
            listOf("organization-service", "orgdirectory-service", "client").forEach { module ->
                ports[module] = ServerSocket(0).use { it.localPort }
                startService(module)
                awaitReady(module)
            }
        } catch (failure: Throwable) {
            stopStack()
            throw failure
        }
    }

    @AfterAll
    fun stopStack() {
        processes.values.toList().asReversed().forEach { stopProcess(it) }
        processes.clear()
        database.stop()
    }

    @BeforeEach
    fun clearDatabase() {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute("TRUNCATE s389491.employees, s389491.organizations RESTART IDENTITY CASCADE") }
        }
    }

    @Test
    fun `client CRUD stores records in production schema and preserves generated fields`() {
        val created = request("POST", "/api/organizations", organization("Acme", 100), 201)
        val id = created.getAttribute("id")
        val date = created.text("creationDate")
        assertFalse(date.isBlank())
        assertEquals("Acme", request("GET", "/api/organizations/$id").text("name"))
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.prepareStatement("SELECT name FROM s389491.organizations WHERE id = ?").use { statement ->
                statement.setInt(1, id.toInt())
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Acme", rows.getString(1))
                }
            }
        }
        val updated = request("PUT", "/api/organizations/$id", organization("Updated", 200))
        assertEquals("Updated", updated.text("name"))
        assertEquals(date, updated.text("creationDate"))
        request("DELETE", "/api/organizations/$id", expected = 204)
        assertEquals("404", request("GET", "/api/organizations/$id", expected = 404).text("code"))
    }

    @Test
    fun `client forwards filters sort and page parameters to the database service`() {
        create("Alpha", 30)
        create("Beta", 10)
        create("Gamma", 20)
        val filtered = request("GET", "/api/organizations?name=Beta&type=COMMERCIAL")
        assertEquals("1", filtered.text("total"))
        assertEquals(listOf("Beta"), filtered.organizations().map { it.text("name") })
        val middle = request("GET", "/api/organizations?sortBy=annualTurnover&sortOrder=asc&page=2&size=1")
        assertEquals("3", middle.text("total"))
        assertEquals("2", middle.text("page"))
        assertEquals(listOf("Gamma"), middle.organizations().map { it.text("name") })
        val descending = request("GET", "/api/organizations?sortBy=name&sortOrder=desc")
        assertEquals(listOf("Gamma", "Beta", "Alpha"), descending.organizations().map { it.text("name") })
    }

    @Test
    fun `directory filters employees and sorts through both real upstream services`() {
        val alpha = create("Alpha", 100)
        val beta = create("Beta", 200)
        val employee = request("POST", "/api/organizations/$alpha/employees", employee("Ada"), 201)
        request("POST", "/api/organizations/$beta/employees", employee("Grace"), 201)
        request("POST", "/api/organizations/$beta/employees", employee("Linus"), 201)
        assertEquals(1, request("GET", "/api/organizations/$alpha/employees").getElementsByTagName("employee").length)
        val matches = request("GET", "/api/directory/filter/employees/1/1")
        assertEquals("1", matches.text("total"))
        assertEquals(listOf(alpha), matches.organizations().map { it.getAttribute("id") })
        val sorted = request("GET", "/api/directory/order/name/true")
        assertEquals(listOf("Beta", "Alpha"), sorted.organizations().map { it.text("name") })
        request("DELETE", "/api/organizations/$alpha/employees/${employee.getAttribute("id")}", expected = 204)
        val afterDelete = request("GET", "/api/directory/filter/employees/0/0")
        assertEquals(listOf(alpha), afterDelete.organizations().map { it.getAttribute("id") })
    }

    @Test
    fun `aggregation results cross the client proxy`() {
        create("Same", 100)
        create("Same", 200)
        val groups = request("GET", "/api/organizations/stats/grouped-by-name")
        assertEquals("Same", groups.text("name"))
        assertEquals("2", groups.text("count"))
        assertEquals("1", request("GET", "/api/organizations/stats/annual-turnover/less-than/150").text("count"))
        val unique = request("GET", "/api/organizations/stats/annual-turnover/unique").getElementsByTagName("value")
        assertEquals(setOf("100", "200"), (0 until unique.length).map { unique.item(it).textContent }.toSet())
    }

    @Test
    fun `validation errors from each service reach the client with their status and message`() {
        val bad = request("POST", "/api/organizations", organization("", 100), 400)
        assertEquals("400", bad.text("code"))
        assertTrue(bad.text("message").contains("name"))
        request("GET", "/api/organizations?page=0", expected = 400)
        request("GET", "/api/organizations?sortBy=unknown", expected = 400)
        val range = request("GET", "/api/directory/filter/employees/2/1", expected = 400)
        assertEquals("400", range.text("code"))
        assertFalse(range.text("message").isBlank())
        request("GET", "/api/directory/order/unknown/false", expected = 400)
        request("GET", "/api/organizations/999999/employees", expected = 404)
    }

    @Test
    fun `production stack serves client assets over TLS and rejects plaintext requests`() {
        listOf("/", "/app.js", "/style.css").forEach { path ->
            val response = send("GET", path)
            assertEquals(200, response.statusCode(), path)
            assertFalse(response.body().isBlank())
        }
        assertTrue(send("GET", "/").body().contains("Каталог организаций"))
        ports.forEach { (module, port) ->
            val path = if (module == "client") "/" else if (module == "organization-service") "/organizations" else "/orgdirectory/order/name/false"
            try {
                val plain = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                assertTrue(plain.statusCode() >= 400, "$module accepted a plaintext request")
            } catch (_: IOException) {
                // A TLS-only listener can close the connection when it receives plaintext.
            }
        }
    }

    @Test
    fun `client reports an unavailable directory and recovers after service restart`() {
        val directory = requireNotNull(processes.remove("orgdirectory-service"))
        stopProcess(directory)
        try {
            val error = request("GET", "/api/directory/order/name/false", expected = 502)
            assertEquals("502", error.text("code"))
            assertFalse(error.text("message").isBlank())
        } finally {
            startService("orgdirectory-service")
            awaitReady("orgdirectory-service")
        }
        request("GET", "/api/directory/order/name/false")
    }

    private fun create(name: String, turnover: Int) = request("POST", "/api/organizations", organization(name, turnover), 201).getAttribute("id")
    private fun organization(name: String, turnover: Int) = "<organization><name>$name</name><coordinates><x>1.5</x><y>2</y></coordinates><annualTurnover>$turnover</annualTurnover><type>COMMERCIAL</type><postalAddress><street>Main street</street></postalAddress></organization>"
    private fun employee(name: String) = "<employee><name>$name</name><position>Engineer</position><salary>5000</salary></employee>"

    private fun request(method: String, path: String, body: String? = null, expected: Int = 200): Element {
        val response = send(method, path, body)
        assertEquals(expected, response.statusCode(), "$method $path: ${response.body()}")
        val xml = if (expected == 204) "<empty/>" else response.body()
        return DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun send(method: String, path: String, body: String? = null, module: String = "client"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("${base(module)}$path")).timeout(Duration.ofSeconds(25))
            .header("Accept", "application/xml")
        if (body != null) builder.header("Content-Type", "application/xml")
        return http.send(builder.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun Element.text(tag: String): String = if (tagName == tag) textContent
        else getElementsByTagName(tag).item(0)?.textContent ?: error("Missing <$tag> in <$tagName>")
    private fun Element.organizations(): List<Element> = getElementsByTagName("organization").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun base(module: String) = "https://localhost:${ports.getValue(module)}"

    private fun createCertificate() {
        Files.deleteIfExists(keystore)
        val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
        val process = ProcessBuilder(keytool, "-genkeypair", "-alias", "soa-e2e", "-keyalg", "RSA", "-storetype", "PKCS12",
            "-keystore", keystore.toString(), "-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=localhost",
            "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-noprompt")
            .redirectErrorStream(true).redirectOutput(work.resolve("keytool.log").toFile()).start()
        try {
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Certificate generation failed; see ${work.resolve("keytool.log")}" }
        } finally { stopProcess(process) }
    }

    private fun startService(module: String) {
        val artifact = root.resolve("$module/build/$module-1.0.0-runner.jar")
        check(Files.isRegularFile(artifact)) { "Missing packaged application: $artifact" }
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val builder = ProcessBuilder(java, "-Xmx256m", "-Dquarkus.profile=prod", "-Dquarkus.http.port=0",
            "-Djavax.net.ssl.trustStore=$keystore", "-Djavax.net.ssl.trustStorePassword=changeit", "-Djavax.net.ssl.trustStoreType=PKCS12",
            "-jar", artifact.toString()).directory(root.toFile())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(work.resolve("$module.log").toFile()))
        builder.environment().putAll(mapOf(
            "PORT" to ports.getValue(module).toString(), "TLS_KEYSTORE" to keystore.toString(), "TLS_KEYSTORE_PASSWORD" to "changeit",
            "DB_URL" to "${database.jdbcUrl}${if ('?' in database.jdbcUrl) '&' else '?'}currentSchema=s389491", "DB_USER" to database.username, "DB_PASSWORD" to database.password,
            "ORGANIZATION_SERVICE_URL" to base("organization-service"),
            "ORG_DIRECTORY_SERVICE_URL" to (ports["orgdirectory-service"]?.let { "https://localhost:$it/orgdirectory" } ?: "https://localhost:1/orgdirectory")
        ))
        processes[module] = builder.start()
    }

    private fun awaitReady(module: String) {
        val path = when (module) {
            "organization-service" -> "/organizations"
            "orgdirectory-service" -> "/orgdirectory/order/name/false"
            else -> "/api/organizations"
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        var lastFailure: String? = null
        while (System.nanoTime() < deadline && processes.getValue(module).isAlive) {
            try {
                val response = send("GET", path, module = module)
                if (response.statusCode() == 200) return
                lastFailure = "HTTP ${response.statusCode()}: ${response.body()}"
            } catch (e: IOException) { lastFailure = e.message }
            Thread.sleep(200)
        }
        error("$module failed to start: $lastFailure\n${Files.readString(work.resolve("$module.log"))}")
    }

    private fun stopProcess(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
        }
    }
}

private class TestPostgres : PostgreSQLContainer<TestPostgres>("postgres:16-alpine")
