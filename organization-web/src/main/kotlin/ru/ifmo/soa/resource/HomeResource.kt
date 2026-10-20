package ru.ifmo.soa.resource

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import java.net.URI

@Path("/")
class HomeResource {
    @GET
    fun redirectToUi(): Response = Response
        .status(Response.Status.FOUND)
        .location(URI.create("/ui/"))
        .build()
}
