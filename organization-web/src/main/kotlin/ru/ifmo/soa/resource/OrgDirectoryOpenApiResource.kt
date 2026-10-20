package ru.ifmo.soa.resource

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces

/** Keeps the Lab 2 documentation URL working while OrgDirectory runs as Spring Boot. */
@Path("/openapi/orgdirectory")
class OrgDirectoryOpenApiResource {
    @GET
    @Produces("application/yaml", "text/yaml")
    fun specification(): String = requireNotNull(javaClass.classLoader.getResourceAsStream("META-INF/openapi-orgdirectory.yaml")) {
        "OrgDirectory OpenAPI document is missing"
    }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
