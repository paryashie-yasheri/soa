package ru.ifmo.soa.repository

import jakarta.enterprise.context.ApplicationScoped
import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

data class OrganizationDocument(val id: Int, val xml: String)

@ApplicationScoped
class OrganizationApiRepository {
    private val base: String = System.getProperty("organization-service.url")
        ?: System.getenv("ORGANIZATION_SERVICE_URL")
        ?: "https://localhost:61811"

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    }

    fun allOrganizations(sortBy: String? = null, descending: Boolean = false): List<OrganizationDocument> {
        var page = 1
        var total = Int.MAX_VALUE
        val result = mutableListOf<OrganizationDocument>()
        val ids = mutableSetOf<Int>()
        while (result.size < total) {
            val sort = sortBy?.let {
                "&sortBy=${encode(it)}&sortOrder=${if (descending) "desc" else "asc"}"
            } ?: ""
            val root = parse(get("/organizations?page=$page&size=100$sort")).documentElement
            check(root.tagName == "organizations") { "Organization Service вернул неверный корневой XML-элемент" }
            val pageTotal = root.child("total")?.textContent?.trim()?.toIntOrNull()
                ?: error("Organization Service вернул XML без корректного total")
            check(pageTotal >= 0) { "Некорректная страница Organization Service" }
            if (page == 1) total = pageTotal else {
                check(pageTotal == total) { "Organization Service изменил total в середине пагинации" }
            }
            val items = children(root)
                .filter { it.tagName == "organization" }
                .map { element ->
                    val id = element.getAttribute("id").toIntOrNull()
                    check(id != null && id > 0) { "Organization Service вернул некорректный id" }
                    check(ids.add(id)) { "Organization Service повторил организацию при пагинации" }
                    OrganizationDocument(id, serialize(element))
                }
            check(result.size + items.size <= total) { "Organization Service вернул больше элементов, чем total" }
            check(items.isNotEmpty() || result.size >= total) { "Organization Service вернул пустую страницу до конца результата" }
            result += items
            page++
            if (page > 10000 && result.size < total) error("Слишком много страниц от Organization Service")
        }
        return result
    }

    fun employeeCount(organizationId: Int): Int {
        val root = parse(get("/organizations/$organizationId/employees")).documentElement
        return children(root).count { it.tagName == "employee" }
    }

    private fun get(path: String): String {
        val request = HttpRequest.newBuilder(URI.create(base.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/xml")
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: java.net.http.HttpTimeoutException) {
            throw DirectoryTimeout()
        }
        if (response.statusCode() !in 200..299) {
            error("Organization Service вернул HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder().parse(InputSource(StringReader(xml)))

    private fun children(element: Element): List<Element> =
        (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }

    private fun Element.child(name: String): Element? =
        children(this).firstOrNull { it.tagName == name }

    private fun serialize(element: Element): String = StringWriter().also { writer ->
        TransformerFactory.newInstance().newTransformer()
            .apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
            .transform(DOMSource(element), StreamResult(writer))
    }.toString()

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

class DirectoryTimeout : RuntimeException()
