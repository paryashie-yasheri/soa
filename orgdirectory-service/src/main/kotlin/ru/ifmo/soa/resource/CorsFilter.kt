package ru.ifmo.soa.resource

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.container.PreMatching

/**
 * The browser client is served from a different origin (static hosting on port 443),
 * so every response must carry CORS headers and preflight requests must be answered here.
 */
@Provider
@PreMatching
class CorsFilter : ContainerRequestFilter, ContainerResponseFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        if (requestContext.method.equals("OPTIONS", ignoreCase = true)) {
            requestContext.abortWith(
                Response.ok()
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", ALLOWED_METHODS)
                    .header("Access-Control-Allow-Headers", ALLOWED_HEADERS)
                    .header("Access-Control-Max-Age", "3600")
                    .build()
            )
        }
    }

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val headers = responseContext.headers
        headers.putSingle("Access-Control-Allow-Origin", "*")
        headers.putSingle("Access-Control-Allow-Methods", ALLOWED_METHODS)
        headers.putSingle("Access-Control-Allow-Headers", ALLOWED_HEADERS)
    }

    private companion object {
        const val ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS"
        const val ALLOWED_HEADERS = "Content-Type, Accept"
    }
}
