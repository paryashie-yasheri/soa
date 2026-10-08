package ru.ifmo.soa.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import ru.ifmo.soa.model.Employee
import ru.ifmo.soa.model.EmployeeInput
import ru.ifmo.soa.repository.EmployeeRepository
import ru.ifmo.soa.repository.OrganizationRepository

@ApplicationScoped
class EmployeeService {
    @field:Inject
    private lateinit var organizations: OrganizationRepository

    @field:Inject
    private lateinit var employees: EmployeeRepository

    @Transactional
    fun list(organizationId: Int): List<Employee> {
        checkOrganizationId(organizationId)
        if (organizations.entityById(organizationId) == null) throw ServiceFault(404, "Организация не найдена")
        return employees.byOrganization(organizationId)
    }

    @Transactional
    fun add(organizationId: Int, input: EmployeeInput): Employee {
        checkOrganizationId(organizationId)
        if (input.name.isEmpty()) throw ServiceFault(400, "Поле name не может быть пустым")
        if (input.salary != null && (input.salary < 0 || !input.salary.isFinite())) {
            throw ServiceFault(400, "salary должен быть неотрицательным")
        }
        val organization = organizations.entityById(organizationId) ?: throw ServiceFault(404, "Организация не найдена")
        return employees.add(organization, input)
    }

    @Transactional
    fun delete(organizationId: Int, employeeId: Int) {
        checkOrganizationId(organizationId)
        if (employeeId < 1) throw ServiceFault(400, "employeeId должен быть больше 0")
        if (!employees.remove(organizationId, employeeId)) throw ServiceFault(404, "Сотрудник не найден")
    }

    private fun checkOrganizationId(id: Int) {
        if (id < 1) throw ServiceFault(400, "id должен быть больше 0")
    }
}