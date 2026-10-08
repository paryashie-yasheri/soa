package ru.ifmo.soa.resource

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * Отвечает XML-ошибкой на неизвестные пути (404) и тела с неподдерживаемым
 * Content-Type (415), как описано в спецификации.
 */
@Provider
class HttpErrorMapper : ExceptionMapper<WebApplicationException> {
    override fun toResponse(error: WebApplicationException): Response {
        val code = error.response.status
        val message = when (code) {
            404 -> "Запрошенная ручка не найдена"
            415 -> "Ожидался формат application/xml"
            else -> "Ошибка HTTP $code"
        }
        return errorResponse(code, message)
    }
}
