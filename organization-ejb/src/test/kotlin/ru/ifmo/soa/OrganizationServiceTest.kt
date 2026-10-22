package ru.ifmo.soa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.model.*
import ru.ifmo.soa.repository.OrganizationRepository
import ru.ifmo.soa.service.OrganizationService
import ru.ifmo.soa.service.ServiceFault
import java.time.LocalDate

class OrganizationServiceTest {
    private open class FakeRepository : OrganizationRepository() {
        val rows = mutableListOf(
            Organization(1, "Beta", Coordinates(2f, 10), LocalDate.parse("2024-01-02"), null, null, OrganizationType.TRUST, Address("Oak")),
            Organization(2, "Alpha", Coordinates(-1f, 20), LocalDate.parse("2024-01-01"), 20, "A", OrganizationType.COMMERCIAL, Address("Pine")),
            Organization(3, "Alpha", Coordinates(2f, 30), LocalDate.parse("2024-01-03"), 10, "B", OrganizationType.OPEN_JOINT_STOCK_COMPANY, Address("Oak"))
        )
        var created: OrganizationInput? = null
        var updated: Pair<Int, OrganizationInput>? = null
        var removed: Int? = null
        var updateResult: Organization? = null
        var removeResult = true

        override fun allModels() = rows.toList()
        override fun modelById(id: Int) = rows.firstOrNull { it.id == id }
        override fun create(input: OrganizationInput): Organization {
            created = input
            return rows.first().copy(id = 4, name = input.name)
        }
        override fun update(id: Int, input: OrganizationInput): Organization? {
            updated = id to input
            return updateResult
        }
        override fun removeOrganization(id: Int): Boolean { removed = id; return removeResult }
    }

    private fun service(repo: FakeRepository) = OrganizationService().also {
        it.javaClass.getDeclaredField("repository").apply { isAccessible = true }.set(it, repo)
    }

    private fun input(
        name: String = "Org", x: Float = 1f, turnover: Int? = 1, street: String = "St"
    ) = OrganizationInput(name, Coordinates(x, 2), turnover, null, OrganizationType.TRUST, Address(street))

    @Test
    fun `CRUD delegates after validation and returns not found for absent entities`() {
        val repo = FakeRepository()
        val service = service(repo)
        val payload = input()

        assertEquals(4, service.create(payload).id)
        assertEquals(payload, repo.created)
        assertEquals(repo.rows[1], service.get(2))
        assertEquals("id должен быть больше 0", assertThrows(ServiceFault::class.java) { service.get(0) }.message)
        assertEquals(404, assertThrows(ServiceFault::class.java) { service.get(99) }.status)
        assertEquals(400, assertThrows(ServiceFault::class.java) { service.update(0, payload) }.status)

        repo.updateResult = repo.rows.first().copy(name = "changed")
        assertEquals("changed", service.update(1, payload).name)
        assertEquals(1 to payload, repo.updated)
        repo.updateResult = null
        assertEquals(404, assertThrows(ServiceFault::class.java) { service.update(10, payload) }.status)
        service.delete(3)
        assertEquals(3, repo.removed)
        repo.removeResult = false
        assertEquals(404, assertThrows(ServiceFault::class.java) { service.delete(9) }.status)
        assertEquals(400, assertThrows(ServiceFault::class.java) { service.delete(-1) }.status)
    }

    @Test
    fun `validation covers finite coordinates nonempty name positive turnover and street limit`() {
        val service = service(FakeRepository())
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { x ->
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.create(input(x = x)) }.status)
        }
        assertEquals(400, assertThrows(ServiceFault::class.java) { service.create(input(name = "")) }.status)
        listOf(0, -1).forEach { value ->
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.create(input(turnover = value)) }.status)
        }
        assertEquals(400, assertThrows(ServiceFault::class.java) { service.create(input(street = "x".repeat(159))) }.status)
        assertDoesNotThrow { service.create(input(turnover = null, street = "x".repeat(158))) }
    }

    @Test
    fun `list filters all fields sorts by multiple keys and paginates safely`() {
        val repo = FakeRepository()
        val service = service(repo)
        val matching = service.list(OrganizationFilter(name = "Alpha", coordinatesX = 2f,
            coordinatesY = 30, creationDate = LocalDate.parse("2024-01-03"), annualTurnover = 10,
            fullName = "B", type = OrganizationType.OPEN_JOINT_STOCK_COMPANY, street = "Oak"), emptyList(), false, 1, 10)
        assertEquals(listOf(3), matching.items.map { it.id })
        assertEquals(listOf(2, 3, 1), service.list(OrganizationFilter(), listOf("name", "id"), false, 1, 10).items.map { it.id })
        assertEquals(listOf(1, 3, 2), service.list(OrganizationFilter(), listOf("name", "id"), true, 1, 10).items.map { it.id })
        val lastPage = service.list(OrganizationFilter(), listOf("id"), false, 2, 2)
        assertEquals(3, lastPage.total)
        assertEquals(listOf(3), lastPage.items.map { it.id })
        assertTrue(service.list(OrganizationFilter(), emptyList(), false, Int.MAX_VALUE, 100).items.isEmpty())
        assertEquals(1, service.list(OrganizationFilter(id = 1), emptyList(), false, 1, 10).items.single().id)
    }

    @Test
    fun `list rejects page size and unknown sort before reading repository`() {
        val repo = object : FakeRepository() {
            override fun allModels(): List<Organization> = error("must not query")
        }
        val service = service(repo)
        listOf(0, -1).forEach { page ->
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.list(OrganizationFilter(), emptyList(), false, page, 10) }.status)
        }
        listOf(0, 101).forEach { size ->
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.list(OrganizationFilter(), emptyList(), false, 1, size) }.status)
        }
        assertEquals(400, assertThrows(ServiceFault::class.java) {
            service.list(OrganizationFilter(), listOf("name;drop"), false, 1, 10)
        }.status)
    }

    @Test
    fun `every allowed sort key has deterministic ascending and descending order`() {
        val expected = mapOf(
            "id" to (listOf(1, 2, 3) to listOf(3, 2, 1)),
            "name" to (listOf(2, 3, 1) to listOf(1, 2, 3)),
            "coordinates.x" to (listOf(2, 1, 3) to listOf(1, 3, 2)),
            "coordinates.y" to (listOf(1, 2, 3) to listOf(3, 2, 1)),
            "creationDate" to (listOf(2, 1, 3) to listOf(3, 1, 2)),
            "annualTurnover" to (listOf(1, 3, 2) to listOf(2, 3, 1)),
            "fullName" to (listOf(1, 2, 3) to listOf(3, 2, 1)),
            "type" to (listOf(2, 3, 1) to listOf(1, 3, 2)),
            "postalAddress.street" to (listOf(1, 3, 2) to listOf(2, 1, 3))
        )
        val service = service(FakeRepository())
        expected.forEach { (field, orders) ->
            assertEquals(orders.first, service.list(OrganizationFilter(), listOf(field), false, 1, 10).items.map { it.id }, "ascending $field")
            assertEquals(orders.second, service.list(OrganizationFilter(), listOf(field), true, 1, 10).items.map { it.id }, "descending $field")
        }
    }

    @Test
    fun `statistics count null turnover correctly and return sorted deterministic values`() {
        val service = service(FakeRepository())
        assertEquals(mapOf("Alpha" to 2, "Beta" to 1), service.groupedByName())
        assertEquals(2L, service.countTurnoverBelow(21))
        assertEquals(0L, service.countTurnoverBelow(10))
        assertEquals(400, assertThrows(ServiceFault::class.java) { service.countTurnoverBelow(0) }.status)
        assertEquals(listOf(null, 10, 20), service.uniqueTurnovers())
    }
}
