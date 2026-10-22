package ru.ifmo.soa

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.repository.OrganizationApiRepository
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ru.ifmo.soa.repository.DirectoryTimeout

class OrganizationApiRepositoryTest {
    private fun withUpstream(page: (Int) -> String, check: (OrganizationApiRepository, AtomicInteger) -> Unit) {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            val body = page(calls.incrementAndGet()).toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            check(repository, calls)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `empty page before total is reached fails immediately`() = withUpstream(
        { "<organizations><total>1</total></organizations>" }
    ) { repository, calls ->
        assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }
        assertEquals(1, calls.get())
    }

    @Test
    fun `empty collection returns without requesting another page`() = withUpstream(
        { "<organizations><total>0</total></organizations>" }
    ) { repository, calls ->
        assertTrue(repository.allOrganizations().isEmpty())
        assertEquals(1, calls.get())
    }

    @Test
    fun `pages are collected until total is reached`() = withUpstream(
        { page -> """<organizations><total>2</total><organization id="$page"><name>Item $page</name></organization></organizations>""" }
    ) { repository, calls ->
        assertEquals(listOf(1, 2), repository.allOrganizations().map { it.id })
        assertEquals(2, calls.get())
    }

    @Test
    fun `non-success HTTP status is rejected`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            val body = "upstream unavailable".toByteArray()
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            val error = assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }
            assertTrue(error.message.orEmpty().contains("503"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `negative total is rejected`() = withUpstream(
        { "<organizations><total>-1</total></organizations>" }
    ) { repository, _ ->
        assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }
    }

    @Test
    fun `malformed XML and external entities are rejected`() = withUpstream(
        { "<!DOCTYPE organizations [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><organizations><total>0</total>&xxe;</organizations>" }
    ) { repository, _ ->
        assertThrows(Exception::class.java) { repository.allOrganizations() }
    }

    @Test
    fun `employee count ignores unrelated child nodes`() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            calls.incrementAndGet()
            val body = "<employees><employee/><note>ignored</note><employee/></employees>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            assertEquals(2, repository.employeeCount(17))
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sort parameters are URL encoded and descending order is explicit`() {
        val query = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            query.set(exchange.requestURI.rawQuery)
            val body = "<organizations><total>0</total></organizations>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            assertTrue(repository.allOrganizations("name&sortOrder=asc", descending = true).isEmpty())
            assertEquals("page=1&size=100&sortBy=name%26sortOrder%3Dasc&sortOrder=desc", query.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `not found upstream and malformed organization ids fail clearly`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            val body = "<error>missing</error>".toByteArray()
            exchange.sendResponseHeaders(404, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            assertTrue(assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }.message.orEmpty().contains("404"))
        } finally { server.stop(0) }

        withUpstream({ "<organizations><total>1</total><organization id=\"not-an-id\"/></organizations>" }) { repository, _ ->
            assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }
        }
    }

    @Test
    fun `pagination rejects duplicate ids and a changing total`() {
        withUpstream({ page ->
            val id = if (page == 1) 11 else 11
            "<organizations><total>2</total><organization id=\"$id\"/></organizations>"
        }) { repository, _ -> assertThrows(IllegalStateException::class.java) { repository.allOrganizations() } }

        withUpstream({ page ->
            val total = if (page == 1) 2 else 3
            "<organizations><total>$total</total><organization id=\"$page\"/></organizations>"
        }) { repository, _ -> assertThrows(IllegalStateException::class.java) { repository.allOrganizations() } }
    }

    @Test
    fun `pagination rejects invalid root total and nonpositive ids`() {
        listOf(
            "<unexpected><total>0</total></unexpected>",
            "<organizations><total>not-a-number</total></organizations>",
            "<organizations><total>1</total><organization id=\"0\"/></organizations>",
            "<organizations><total>1</total><organization/></organizations>"
        ).forEach { xml ->
            withUpstream({ xml }) { repository, _ ->
                assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }
            }
        }
    }

    @Test
    fun `pagination rejects more rows than declared total`() = withUpstream(
        { "<organizations><total>1</total><organization id=\"1\"/><organization id=\"2\"/></organizations>" }
    ) { repository, _ -> assertThrows(IllegalStateException::class.java) { repository.allOrganizations() } }

    @Test
    fun `pagination has a hard upper bound on upstream requests`() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            val page = calls.incrementAndGet()
            val body = "<organizations><total>20000</total><organization id=\"$page\"/></organizations>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            assertThrows(IllegalStateException::class.java) { repository.allOrganizations() }
            assertEquals(10_000, calls.get(), "the repository must stop before requesting page 10001")
        } finally { server.stop(0) }
    }

    @Test
    fun `request timeout is translated to DirectoryTimeout`() {
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/organizations") { exchange ->
            release.await(15, TimeUnit.SECONDS)
            runCatching {
                val body = "<organizations><total>0</total></organizations>".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        try {
            val repository = OrganizationApiRepository()
            repository.javaClass.getDeclaredField("base").apply { isAccessible = true }
                .set(repository, "http://127.0.0.1:${server.address.port}")
            assertThrows(DirectoryTimeout::class.java) { repository.allOrganizations() }
        } finally {
            release.countDown()
            server.stop(0)
        }
    }
}
