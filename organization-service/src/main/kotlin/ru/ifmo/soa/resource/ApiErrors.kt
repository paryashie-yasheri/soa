package ru.ifmo.soa.resource

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import ru.ifmo.soa.service.ServiceFault
import java.sql.SQLException
import java.time.DateTimeException
import org.xml.sax.SAXException

internal fun errorResponse(code: Int, message: String): Response = Response.status(code)
    .type(MediaType.APPLICATION_XML)
    .entity(
        "<error><code>$code</code><message>${message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</message></error>"
    ).build()

internal fun organizationError(error: Exception): Response {
    if (error is ServiceFault) {
        return errorResponse(
            error.status,
            if (error.status == 404) "Объект с указанным идентификатором не найден" else error.message
        )
    }
    if (error is IllegalArgumentException || error is SAXException || error is DateTimeException) {
        return errorResponse(400, error.message ?: "Некорректный запрос")
    }
    val causes = generateSequence<Throwable>(error) { it.cause }.take(32).toList()
    val unavailable = causes.any {
        (it is SQLException && (it.sqlState?.startsWith("08") == true || it.sqlState?.startsWith("53") == true ||
            it.sqlState in setOf("57P01", "57P02", "57P03"))) ||
            it.javaClass.simpleName == "JDBCConnectionException"
    }
    return if (unavailable) errorResponse(503, "База данных временно недоступна")
        else errorResponse(500, "Внутренняя ошибка сервиса")
}

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
