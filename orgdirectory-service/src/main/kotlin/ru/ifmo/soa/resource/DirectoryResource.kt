package ru.ifmo.soa.resource

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.ifmo.soa.service.DirectoryFault
import ru.ifmo.soa.service.DirectoryService

@Path("/orgdirectory")
@Produces(MediaType.APPLICATION_XML)
class DirectoryResource(private val service: DirectoryService) {
    @GET
    @Path("/filter/employees/{min-employees-count}/{max-employees-count}")
    fun filter(
        @PathParam("min-employees-count") minimum: Int,
        @PathParam("max-employees-count") maximum: Int
    ) = api {
        Response.ok(organizationList(service.filterByEmployeeCount(minimum, maximum))).build()
    }

    @GET
    @Path("/order/{param-name}/{desc}")
    fun order(
        @PathParam("param-name") field: String,
        @PathParam("desc") descending: String
    ) = api {
        Response.ok(organizationList(service.order(field, descending))).build()
    }

    private fun organizationList(items: List<String>): String = buildString {
        append("<organizations><total>").append(items.size).append("</total>")
        items.forEach { append(it) }
        append("</organizations>")
    }

    private inline fun api(block: () -> Response): Response = try {
        block()
    } catch (e: DirectoryFault) {
        errorResponse(e.status, e.message)
    } catch (e: Exception) {
        errorResponse(500, "Внутренняя ошибка сервиса")
    }

    private fun errorResponse(code: Int, message: String): Response = Response.status(code)
        .type(MediaType.APPLICATION_XML)
        .entity(
            "<error><code>$code</code><message>${message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</message></error>"
        ).build()
}
