package ru.ifmo.soa.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import ru.ifmo.soa.model.Employee
import ru.ifmo.soa.model.EmployeeInput

@ApplicationScoped
class EmployeeRepository : PanacheRepositoryBase<EmployeeEntity, Int> {
    fun byOrganization(organizationId: Int): List<Employee> =
        find("organization.id = ?1 order by id", organizationId).list().map(EmployeeEntity::toModel)

    fun add(organization: OrganizationEntity, input: EmployeeInput): Employee {
        val entity = EmployeeEntity().apply {
            this.organization = organization
            name = input.name
            position = input.position
            salary = input.salary
        }
        persist(entity)
        flush()
        return entity.toModel()
    }

    fun remove(organizationId: Int, employeeId: Int): Boolean {
        val employee = find("id = ?1 and organization.id = ?2", employeeId, organizationId).firstResult()
            ?: return false
        delete(employee)
        return true
    }
}
