package ru.ifmo.soa.resource

import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.ifmo.soa.service.ServiceFault
import java.sql.SQLException
import java.time.DateTimeException
import org.xml.sax.SAXException

internal fun errorResponse(code: Int, message: String): Response = Response.status(code)
    .type(MediaType.APPLICATION_XML)
    .entity(
        "<error><code>$code</code><message>${message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</message></error>"
    ).build()

/**
 * Маппинг исключений на HTTP-ответы по спецификации: 400 — ошибка валидации,
 * 503 — недоступность БД, 500 — прочие непредвиденные ошибки.
 */
internal fun apiErrorResponse(error: Exception): Response = when {
    error is ServiceFault -> errorResponse(error.status, error.message)
    error is IllegalArgumentException || error is SAXException || error is DateTimeException ->
        errorResponse(400, error.message ?: "Некорректный запрос")
    error is SQLException && (error.sqlState?.startsWith("08") == true || error.sqlState?.startsWith("53") == true) ->
        errorResponse(503, "База данных временно недоступна")
    else -> errorResponse(500, "Внутренняя ошибка сервиса")
}
