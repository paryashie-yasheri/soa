package ru.ifmo.soa.resource

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ejb.EJB
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.ifmo.soa.service.OrganizationRemote

@ApplicationScoped
@Path("/organizations/{id}/employees")
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
class EmployeeResource {
    @field:EJB
    private lateinit var service: OrganizationRemote

    @GET
    fun list(@PathParam("id") organizationId: Int): Response =
        api { Response.ok(XmlCodec.employees(service.employees(organizationId))).build() }

    @POST
    fun add(@PathParam("id") organizationId: Int, body: String): Response =
        api {
            val employee = service.addEmployee(organizationId, XmlCodec.employeeInput(body))
            Response.status(201).entity(XmlCodec.employee(employee)).build()
        }

    @DELETE
    @Path("/{employeeId}")
    fun delete(
        @PathParam("id") organizationId: Int,
        @PathParam("employeeId") employeeId: Int
    ): Response = api {
        service.deleteEmployee(organizationId, employeeId)
        Response.noContent().build()
    }
}

private inline fun api(block: () -> Response): Response = try { block() } catch (e: Exception) {
    organizationError(e)
}
