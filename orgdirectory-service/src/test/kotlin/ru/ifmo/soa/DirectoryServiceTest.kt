package ru.ifmo.soa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.repository.OrganizationApiRepository
import ru.ifmo.soa.repository.OrganizationDocument
import ru.ifmo.soa.service.DirectoryFault
import ru.ifmo.soa.service.DirectoryService

class DirectoryServiceTest {
    private open class FakeRepository : OrganizationApiRepository() {
        var allCalls = 0
        var lastSort: String? = null
        var lastDescending = false
        val organizations = listOf(
            OrganizationDocument(1, "<organization id=\"1\"/>") ,
            OrganizationDocument(2, "<organization id=\"2\"/>")
        )

        override fun allOrganizations(sortBy: String?, descending: Boolean): List<OrganizationDocument> {
            allCalls++
            lastSort = sortBy
            lastDescending = descending
            return organizations
        }

        override fun employeeCount(organizationId: Int): Int = when (organizationId) {
            1 -> 0
            2 -> Int.MAX_VALUE
            else -> error("unexpected id")
        }
    }

    private fun serviceWith(repository: FakeRepository): DirectoryService = DirectoryService().also { service ->
        service.javaClass.getDeclaredField("repository").apply { isAccessible = true }.set(service, repository)
    }

    @Test
    fun `employee count boundaries are inclusive and zero is valid`() {
        val repository = FakeRepository()
        val service = serviceWith(repository)

        assertEquals(listOf("<organization id=\"1\"/>"), service.filterByEmployeeCount(0, 0))
        assertEquals(listOf("<organization id=\"2\"/>"), service.filterByEmployeeCount(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `invalid employee count ranges fail before making upstream calls`() {
        val repository = FakeRepository()
        val service = serviceWith(repository)

        listOf(-1 to 0, 2 to 1, 0 to -1).forEach { (minimum, maximum) ->
            val fault = assertThrows(DirectoryFault::class.java) {
                service.filterByEmployeeCount(minimum, maximum)
            }
            assertEquals(400, fault.status)
        }
        assertEquals(0, repository.allCalls)
    }

    @Test
    fun `only documented sort fields and literal booleans are accepted`() {
        val repository = FakeRepository()
        val service = serviceWith(repository)
        val fields = listOf("id", "name", "coordinates.x", "coordinates.y", "creationDate",
            "annualTurnover", "fullName", "type", "postalAddress.street")

        fields.forEach { field ->
            service.order(field, "false")
            assertEquals(field, repository.lastSort)
            assertFalse(repository.lastDescending)
            service.order(field, "true")
            assertTrue(repository.lastDescending)
        }
        listOf("", "ID", "name;drop", "postalAddress", "null").forEach { field ->
            assertEquals(400, assertThrows(DirectoryFault::class.java) { service.order(field, "false") }.status)
        }
        listOf("TRUE", "1", "", "yes").forEach { direction ->
            assertEquals(400, assertThrows(DirectoryFault::class.java) { service.order("id", direction) }.status)
        }
    }

    @Test
    fun `upstream failures map to gateway errors`() {
        val timeoutRepository = object : FakeRepository() {
            override fun allOrganizations(sortBy: String?, descending: Boolean): List<OrganizationDocument> =
                throw ru.ifmo.soa.repository.DirectoryTimeout()
        }
        assertEquals(504, assertThrows(DirectoryFault::class.java) {
            serviceWith(timeoutRepository).order("id", "false")
        }.status)

        val failedRepository = object : FakeRepository() {
            override fun allOrganizations(sortBy: String?, descending: Boolean): List<OrganizationDocument> =
                throw IllegalStateException("upstream malformed response")
        }
        assertEquals(502, assertThrows(DirectoryFault::class.java) {
            serviceWith(failedRepository).order("id", "false")
        }.status)
    }
}
