package ru.ifmo.soa.service

import jakarta.ejb.Remote
import ru.ifmo.soa.model.*

/** Remote business contract shared with the JAX-RS web module. */
@Remote
interface OrganizationRemote {
    fun list(
        filter: OrganizationFilter,
        sortBy: List<String>,
        descending: Boolean,
        page: Int,
        size: Int,
    ): OrganizationPage

    fun get(id: Int): Organization
    fun create(input: OrganizationInput): Organization
    fun update(id: Int, input: OrganizationInput): Organization
    fun delete(id: Int)
    fun groupedByName(): Map<String, Int>
    fun countTurnoverBelow(value: Int): Long
    fun uniqueTurnovers(): List<Int?>
    fun employees(organizationId: Int): List<Employee>
    fun addEmployee(organizationId: Int, input: EmployeeInput): Employee
    fun deleteEmployee(organizationId: Int, employeeId: Int)
}
