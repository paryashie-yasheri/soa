package ru.ifmo.soa.resource

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.ifmo.soa.service.EmployeeService

@ApplicationScoped
@Path("/organizations/{id}/employees")
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
class EmployeeResource {
    @field:Inject
    private lateinit var service: EmployeeService

    @GET
    fun list(@PathParam("id") organizationId: Int): Response =
        api { Response.ok(XmlCodec.employees(service.list(organizationId))).build() }

    @POST
    fun add(@PathParam("id") organizationId: Int, body: String): Response =
        api {
            val employee = service.add(organizationId, XmlCodec.employeeInput(body))
            Response.status(201).entity(XmlCodec.employee(employee)).build()
        }

    @DELETE
    @Path("/{employeeId}")
    fun delete(
        @PathParam("id") organizationId: Int,
        @PathParam("employeeId") employeeId: Int
    ): Response = api {
        service.delete(organizationId, employeeId)
        Response.noContent().build()
    }
}

private inline fun api(block: () -> Response): Response = try { block() } catch (e: Exception) {
    organizationError(e)
}
