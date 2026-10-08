package ru.ifmo.soa.resource

import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import ru.ifmo.soa.repository.RemoteResult
import ru.ifmo.soa.service.ProxyFault
import ru.ifmo.soa.service.ProxyService

@Path("/api")
class ProxyResource(private val service: ProxyService) {
    @GET
    @Path("{path: .+}")
    @Produces(MediaType.APPLICATION_XML)
    fun get(@PathParam("path") path: String, @Context uri: UriInfo) =
        api { service.forward("GET", path, uri.requestUri.rawQuery, null) }

    @POST
    @Path("{path: .+}")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    fun post(@PathParam("path") path: String, @Context uri: UriInfo, body: String) =
        api { service.forward("POST", path, uri.requestUri.rawQuery, body) }

    @PUT
    @Path("{path: .+}")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    fun put(@PathParam("path") path: String, @Context uri: UriInfo, body: String) =
        api { service.forward("PUT", path, uri.requestUri.rawQuery, body) }

    @DELETE
    @Path("{path: .+}")
    @Produces(MediaType.APPLICATION_XML)
    fun delete(@PathParam("path") path: String, @Context uri: UriInfo) =
        api { service.forward("DELETE", path, uri.requestUri.rawQuery, null) }

    private inline fun api(block: () -> RemoteResult): Response = try {
        val result = block()
        Response.status(result.status).type(MediaType.APPLICATION_XML).entity(result.body).build()
    } catch (e: ProxyFault) {
        val message = (e.message ?: "Ошибка сервиса")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        Response.status(e.status).type(MediaType.APPLICATION_XML)
            .entity("<error><code>${e.status}</code><message>$message</message></error>").build()
    }
}