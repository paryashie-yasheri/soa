package ru.ifmo.soa.service

import jakarta.enterprise.context.ApplicationScoped
import ru.ifmo.soa.repository.RemoteApiRepository
import ru.ifmo.soa.repository.RemoteResult
import ru.ifmo.soa.repository.RemoteTimeout

@ApplicationScoped
class ProxyService(private val remote: RemoteApiRepository) {
    fun forward(method: String, path: String, query: String?, body: String?): RemoteResult {
        if (path.isBlank() || path.split('/').any { it == "." || it == ".." } || path.any { it.isISOControl() }) {
            throw ProxyFault(400, "Недопустимый путь API")
        }
        val directory = path.startsWith("directory/")
        val upstreamPath = if (directory) path.removePrefix("directory/") else path
        return try { remote.send(directory, upstreamPath, query, method, body) }
        catch (_: RemoteTimeout) { throw ProxyFault(504, "Истекло время ожидания сервиса") }
        catch (e: Exception) { throw ProxyFault(502, "Не удалось связаться с сервисом: ${e.message ?: "ошибка соединения"}") }
    }
}

class ProxyFault(val status: Int, override val message: String) : RuntimeException(message)
