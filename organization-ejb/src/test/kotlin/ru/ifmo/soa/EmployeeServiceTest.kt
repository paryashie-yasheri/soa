package ru.ifmo.soa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.model.Employee
import ru.ifmo.soa.model.EmployeeInput
import ru.ifmo.soa.repository.EmployeeRepository
import ru.ifmo.soa.repository.OrganizationEntity
import ru.ifmo.soa.repository.OrganizationRepository
import ru.ifmo.soa.service.EmployeeService
import ru.ifmo.soa.service.ServiceFault

class EmployeeServiceTest {
    private class FakeOrganizations : OrganizationRepository() {
        var lookupId: Int? = null
        var exists = true
        override fun entityById(id: Int): OrganizationEntity? {
            lookupId = id
            return if (exists) OrganizationEntity().also { it.id = id } else null
        }
    }

    private class FakeEmployees : EmployeeRepository() {
        var listId: Int? = null
        var added: EmployeeInput? = null
        var removed: Pair<Int, Int>? = null
        var removeResult = true
        override fun byOrganization(organizationId: Int): List<Employee> {
            listId = organizationId
            return listOf(Employee(1, organizationId, "Ada", null, 0.0))
        }
        override fun add(organization: OrganizationEntity, input: EmployeeInput): Employee {
            added = input
            return Employee(2, organization.id, input.name, input.position, input.salary)
        }
        override fun remove(organizationId: Int, employeeId: Int): Boolean {
            removed = organizationId to employeeId
            return removeResult
        }
    }

    private fun service(orgs: FakeOrganizations, employees: FakeEmployees) = EmployeeService().also { service ->
        service.javaClass.getDeclaredField("organizations").apply { isAccessible = true }.set(service, orgs)
        service.javaClass.getDeclaredField("employees").apply { isAccessible = true }.set(service, employees)
    }

    @Test
    fun `list and add require an existing positive organization`() {
        val orgs = FakeOrganizations()
        val employees = FakeEmployees()
        val service = service(orgs, employees)
        assertEquals("Ada", service.list(7).single().name)
        assertEquals(7, employees.listId)
        assertEquals(7, orgs.lookupId)

        val input = EmployeeInput("Grace", "Engineer", 0.0)
        assertEquals(Employee(2, 8, "Grace", "Engineer", 0.0), service.add(8, input))
        assertEquals(input, employees.added)
        assertEquals(8, orgs.lookupId)

        orgs.exists = false
        assertEquals(404, assertThrows(ServiceFault::class.java) { service.list(9) }.status)
        assertEquals(404, assertThrows(ServiceFault::class.java) { service.add(9, input) }.status)
        listOf(0, -1).forEach { id ->
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.list(id) }.status)
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.add(id, input) }.status)
        }
    }

    @Test
    fun `employee input rejects empty name and negative or nonfinite salary`() {
        val service = service(FakeOrganizations(), FakeEmployees())
        assertEquals(400, assertThrows(ServiceFault::class.java) { service.add(1, EmployeeInput("", null, null)) }.status)
        listOf(-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { salary ->
            assertEquals(400, assertThrows(ServiceFault::class.java) {
                service.add(1, EmployeeInput("Ada", null, salary))
            }.status)
        }
        assertDoesNotThrow { service.add(1, EmployeeInput("Ada", null, null)) }
    }

    @Test
    fun `delete validates both identifiers and reports missing employee`() {
        val repo = FakeEmployees()
        val service = service(FakeOrganizations(), repo)
        service.delete(3, 4)
        assertEquals(3 to 4, repo.removed)
        listOf(0, -1).forEach { invalid ->
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.delete(invalid, 4) }.status)
            assertEquals(400, assertThrows(ServiceFault::class.java) { service.delete(3, invalid) }.status)
        }
        repo.removeResult = false
        assertEquals(404, assertThrows(ServiceFault::class.java) { service.delete(3, 4) }.status)
    }
}
