package ru.ifmo.soa.resource

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import ru.ifmo.soa.model.*
import ru.ifmo.soa.service.OrganizationService
import java.time.LocalDate

@ApplicationScoped
@Path("/organizations")
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
class OrganizationResource {
    @field:Inject
    private lateinit var service: OrganizationService

    @GET
    fun list(@Context uri: UriInfo): Response = api {
        val q = uri.queryParameters
        val id = q.getFirst("id")?.toInt()?.also { require(it > 0) { "id должен быть больше 0" } }
        val x = q.getFirst("coordinates.x")?.toFloat()?.also {
            require(it.isFinite()) { "coordinates.x должен быть конечным числом" }
        }
        val y = q.getFirst("coordinates.y")?.toLong()
        val date = q.getFirst("creationDate")?.let { LocalDate.parse(it) }
        val turnover = q.getFirst("annualTurnover")?.toInt()?.also {
            require(it > 0) { "annualTurnover должен быть больше 0" }
        }
        val type = q.getFirst("type")?.let { OrganizationType.valueOf(it) }
        val street = q.getFirst("postalAddress.street")?.also {
            require(it.length <= 158) { "postalAddress.street не может быть длиннее 158 символов" }
        }
        val filter = OrganizationFilter(
            id, q.getFirst("name"), x, y, date, turnover, q.getFirst("fullName"), type, street
        )
        val order = q.getFirst("sortOrder") ?: "asc"
        require(order == "asc" || order == "desc") { "sortOrder должен быть asc или desc" }
        val result = service.list(
            filter,
            q["sortBy"] ?: emptyList(),
            order == "desc",
            (q.getFirst("page") ?: "1").toInt(),
            (q.getFirst("size") ?: "10").toInt()
        )
        Response.ok(XmlCodec.organizations(result)).build()
    }

    @POST
    fun create(body: String): Response = api {
        val created = service.create(XmlCodec.organizationInput(body))
        Response.status(201).entity(XmlCodec.organization(created)).build()
    }

    @GET
    @Path("/{id}")
    fun get(@PathParam("id") id: Int): Response =
        api { Response.ok(XmlCodec.organization(service.get(id))).build() }

    @PUT
    @Path("/{id}")
    fun update(@PathParam("id") id: Int, body: String): Response = api {
        val updated = service.update(id, XmlCodec.organizationInput(body))
        Response.ok(XmlCodec.organization(updated)).build()
    }

    @DELETE
    @Path("/{id}")
    fun delete(@PathParam("id") id: Int): Response = api {
        service.delete(id)
        Response.noContent().build()
    }

    @GET
    @Path("/stats/grouped-by-name")
    fun grouped(): Response =
        api { Response.ok(XmlCodec.groups(service.groupedByName())).build() }

    @GET
    @Path("/stats/annual-turnover/less-than/{value}")
    fun less(@PathParam("value") value: Int): Response =
        api { Response.ok(XmlCodec.count(service.countTurnoverBelow(value))).build() }

    @GET
    @Path("/stats/annual-turnover/unique")
    fun unique(): Response =
        api { Response.ok(XmlCodec.uniqueTurnovers(service.uniqueTurnovers())).build() }
}

private inline fun api(block: () -> Response): Response = try { block() } catch (e: Exception) {
    organizationError(e)
}
