package ru.ifmo.soa

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ifmo.soa.core.DirectoryFault
import ru.ifmo.soa.core.DirectoryService

@RestController
class DirectoryController(private val service: DirectoryService) {
    @GetMapping("/filter/employees/{min-employees-count}/{max-employees-count}", produces = [MediaType.APPLICATION_XML_VALUE])
    fun filter(
        @PathVariable("min-employees-count") minimum: Int,
        @PathVariable("max-employees-count") maximum: Int
    ): ResponseEntity<String> = api {
        organizationList(service.filterByEmployeeCount(minimum, maximum))
    }

    @GetMapping("/order/{param-name}/{desc}", produces = [MediaType.APPLICATION_XML_VALUE])
    fun order(
        @PathVariable("param-name") field: String,
        @PathVariable("desc") descending: String
    ): ResponseEntity<String> = api { organizationList(service.order(field, descending)) }

    private fun organizationList(items: List<String>) = buildString {
        append("<organizations><total>").append(items.size).append("</total>")
        items.forEach(::append)
        append("</organizations>")
    }

    private fun api(block: () -> String): ResponseEntity<String> = try {
        ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(block())
    } catch (e: DirectoryFault) {
        ResponseEntity.status(e.status).contentType(MediaType.APPLICATION_XML).body(errorXml(e.status, e.message))
    } catch (e: Exception) {
        ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_XML)
            .body(errorXml(500, "Внутренняя ошибка сервиса"))
    }

    private fun errorXml(code: Int, message: String) = "<error><code>$code</code><message>${escapeXml(message)}</message></error>"
    private fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
