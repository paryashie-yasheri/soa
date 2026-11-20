package ru.ifmo.soa.core

import org.springframework.stereotype.Service

class DirectoryFault(val status: Int, override val message: String) : RuntimeException(message)

@Service
class DirectoryService(private val repository: OrganizationApiRepository) {
    private val sortable = setOf(
        "id", "name", "coordinates.x", "coordinates.y", "creationDate",
        "annualTurnover", "fullName", "type", "postalAddress.street"
    )

    fun filterByEmployeeCount(minimum: Int, maximum: Int): List<String> {
        if (minimum < 0 || maximum < minimum) throw DirectoryFault(400, "Диапазон количества сотрудников некорректен")
        return guarded {
            repository.allOrganizations().filter { repository.employeeCount(it.id) in minimum..maximum }.map { it.xml }
        }
    }

    fun order(field: String, descending: String): List<String> {
        if (field !in sortable || descending !in setOf("true", "false")) {
            throw DirectoryFault(400, "Недопустимое поле сортировки или направление")
        }
        return guarded { repository.allOrganizations(field, descending == "true").map { it.xml } }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: DirectoryFault) {
        throw e
    } catch (e: DirectoryTimeout) {
        throw DirectoryFault(504, "Истекло время ожидания ответа от Organization Service")
    } catch (e: Exception) {
        throw DirectoryFault(502, "Organization Service вернул ошибочный ответ")
    }
}
