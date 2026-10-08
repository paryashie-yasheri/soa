package ru.ifmo.soa.repository

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import ru.ifmo.soa.model.Employee
import ru.ifmo.soa.model.EmployeeInput

@ApplicationScoped
class EmployeeRepository {
    @field:PersistenceContext
    private lateinit var entityManager: EntityManager

    fun byOrganization(organizationId: Int): List<Employee> =
        entityManager.createQuery("select e from EmployeeEntity e where e.organization.id = :org order by e.id", EmployeeEntity::class.java)
            .setParameter("org", organizationId).resultList.map { it.toModel() }

    fun add(organization: OrganizationEntity, input: EmployeeInput): Employee {
        val entity = EmployeeEntity().apply {
            this.organization = organization
            name = input.name
            position = input.position
            salary = input.salary
        }
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toModel()
    }

    fun remove(organizationId: Int, employeeId: Int): Boolean {
        val employee = entityManager.createQuery(
            "select e from EmployeeEntity e where e.id = :id and e.organization.id = :org", EmployeeEntity::class.java)
            .setParameter("id", employeeId).setParameter("org", organizationId)
            .resultList.firstOrNull() ?: return false
        entityManager.remove(employee)
        return true
    }
}
