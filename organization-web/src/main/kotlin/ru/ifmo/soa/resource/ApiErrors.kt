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

private const val SQLSTATE_CONNECTION_EXCEPTION_CLASS = "08"
private const val SQLSTATE_INSUFFICIENT_RESOURCES_CLASS = "53"
private const val HIBERNATE_CONNECTION_EXCEPTION = "JDBCConnectionException"

private val databaseShutdownSqlStates = setOf("57P01", "57P02", "57P03")

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
    val unavailable = causes.any { it.indicatesDatabaseUnavailable() }
    return if (unavailable) errorResponse(503, "База данных временно недоступна")
        else errorResponse(500, "Внутренняя ошибка сервиса")
}

private fun Throwable.indicatesDatabaseUnavailable(): Boolean = when (this) {
    is SQLException -> hasUnavailableDatabaseSqlState()
    else -> javaClass.simpleName == HIBERNATE_CONNECTION_EXCEPTION
}

private fun SQLException.hasUnavailableDatabaseSqlState(): Boolean {
    val state = sqlState ?: return false
    return state.startsWith(SQLSTATE_CONNECTION_EXCEPTION_CLASS) ||
        state.startsWith(SQLSTATE_INSUFFICIENT_RESOURCES_CLASS) ||
        state in databaseShutdownSqlStates
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
