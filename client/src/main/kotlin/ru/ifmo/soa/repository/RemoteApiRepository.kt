package ru.ifmo.soa.repository

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class RemoteResult(val status: Int, val body: String)

@ApplicationScoped
class RemoteApiRepository {
    @ConfigProperty(name = "organization-service.url", defaultValue = "https://localhost:9443")
    lateinit var organizations: String
    @ConfigProperty(name = "org-directory-service.url", defaultValue = "https://localhost:9444/orgdirectory")
    lateinit var directory: String
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build() }

    fun send(toDirectory: Boolean, path: String, query: String?, method: String, body: String?): RemoteResult {
        val root = if (toDirectory) directory.trimEnd('/') else organizations.trimEnd('/')
        val target = root + "/" + path + (query?.let { "?$it" } ?: "")
        val builder = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(20)).header("Accept", "application/xml")
        if (body != null) builder.header("Content-Type", "application/xml")
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val response = try { client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString()) }
        catch (e: java.net.http.HttpTimeoutException) { throw RemoteTimeout() }
        return RemoteResult(response.statusCode(), response.body())
    }
}

class RemoteTimeout : RuntimeException()
