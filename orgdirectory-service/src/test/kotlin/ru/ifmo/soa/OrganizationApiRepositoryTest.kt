package ru.ifmo.soa

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.repository.OrganizationApiRepository
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

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
}
