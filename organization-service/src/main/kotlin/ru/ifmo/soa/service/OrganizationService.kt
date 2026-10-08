package ru.ifmo.soa.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import ru.ifmo.soa.model.*
import ru.ifmo.soa.repository.OrganizationRepository

class ServiceFault(val status: Int, override val message: String) : RuntimeException(message)

@ApplicationScoped
class OrganizationService {
    @field:Inject
    private lateinit var repository: OrganizationRepository

    @Transactional
    fun list(filter: OrganizationFilter, sortBy: List<String>, descending: Boolean, page: Int, size: Int): OrganizationPage {
        if (page < 1) throw ServiceFault(400, "page должен быть не меньше 1")
        if (size !in 1..100) throw ServiceFault(400, "size должен быть от 1 до 100")
        val allowed = setOf(
            "id", "name", "coordinates.x", "coordinates.y", "creationDate",
            "annualTurnover", "fullName", "type", "postalAddress.street"
        )
        if (sortBy.any { it !in allowed }) throw ServiceFault(400, "Недопустимое поле сортировки")
        var rows = repository.allModels().filter { o ->
            (filter.id == null || o.id == filter.id) && (filter.name == null || o.name == filter.name) &&
                (filter.coordinatesX == null || o.coordinates.x == filter.coordinatesX) &&
                (filter.coordinatesY == null || o.coordinates.y == filter.coordinatesY) &&
                (filter.creationDate == null || o.creationDate == filter.creationDate) &&
                (filter.annualTurnover == null || o.annualTurnover == filter.annualTurnover) &&
                (filter.fullName == null || o.fullName == filter.fullName) &&
                (filter.type == null || o.type == filter.type) &&
                (filter.street == null || o.postalAddress.street == filter.street)
        }
        if (sortBy.isNotEmpty()) {
            rows = rows.sortedWith(Comparator { a, b ->
                var result = 0
                for (field in sortBy) {
                    result = compare(field, a, b)
                    if (result != 0) break
                }
                if (descending) -result else result
            })
        }
        val offset = ((page - 1L) * size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return OrganizationPage(rows.size, page, size, rows.drop(offset).take(size))
    }

    @Transactional
    fun get(id: Int): Organization {
        if (id < 1) throw ServiceFault(400, "id должен быть больше 0")
        return repository.modelById(id) ?: throw ServiceFault(404, "Организация не найдена")
    }

    @Transactional
    fun create(input: OrganizationInput): Organization {
        validate(input)
        return repository.create(input)
    }

    @Transactional
    fun update(id: Int, input: OrganizationInput): Organization {
        if (id < 1) throw ServiceFault(400, "id должен быть больше 0")
        validate(input)
        return repository.update(id, input) ?: throw ServiceFault(404, "Организация не найдена")
    }

    @Transactional
    fun delete(id: Int) {
        if (id < 1) throw ServiceFault(400, "id должен быть больше 0")
        if (!repository.removeOrganization(id)) throw ServiceFault(404, "Организация не найдена")
    }

    @Transactional
    fun groupedByName(): Map<String, Int> =
        repository.allModels().groupingBy { it.name }.eachCount().toSortedMap()

    @Transactional
    fun countTurnoverBelow(value: Int): Long {
        if (value < 1) throw ServiceFault(400, "value должен быть больше 0")
        return repository.allModels().count { it.annualTurnover != null && it.annualTurnover < value }.toLong()
    }

    @Transactional
    fun uniqueTurnovers(): List<Int?> =
        repository.allModels().map { it.annualTurnover }.distinct().sortedWith(compareBy<Int?> { it })

    private fun validate(input: OrganizationInput) {
        if (!input.coordinates.x.isFinite()) throw ServiceFault(400, "coordinates.x должен быть конечным числом")
        if (input.name.isEmpty()) throw ServiceFault(400, "Поле name не может быть пустым")
        if (input.annualTurnover != null && input.annualTurnover <= 0) throw ServiceFault(400, "annualTurnover должен быть больше 0")
        if (input.postalAddress.street.length > 158) throw ServiceFault(400, "postalAddress.street не может быть длиннее 158 символов")
    }

    private fun compare(field: String, a: Organization, b: Organization): Int = when (field) {
        "id" -> a.id.compareTo(b.id)
        "name" -> a.name.compareTo(b.name)
        "coordinates.x" -> a.coordinates.x.compareTo(b.coordinates.x)
        "coordinates.y" -> a.coordinates.y.compareTo(b.coordinates.y)
        "creationDate" -> a.creationDate.compareTo(b.creationDate)
        "annualTurnover" -> compareValues(a.annualTurnover, b.annualTurnover)
        "fullName" -> compareValues(a.fullName, b.fullName)
        "type" -> a.type.name.compareTo(b.type.name)
        else -> a.postalAddress.street.compareTo(b.postalAddress.street)
    }
}
