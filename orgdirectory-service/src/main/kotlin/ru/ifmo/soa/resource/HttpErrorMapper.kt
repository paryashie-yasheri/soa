package ru.ifmo.soa.resource

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class HttpErrorMapper : ExceptionMapper<WebApplicationException> {
    override fun toResponse(error: WebApplicationException): Response {
        val code = error.response.status
        val message = if (code == 404) "Запрошенная ручка не найдена" else "Ошибка HTTP $code"
        return Response.status(code).type(MediaType.APPLICATION_XML)
            .entity("<error><code>$code</code><message>$message</message></error>").build()
    }
}
