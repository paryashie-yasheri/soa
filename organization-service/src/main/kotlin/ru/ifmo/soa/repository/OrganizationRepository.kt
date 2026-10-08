package ru.ifmo.soa.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import ru.ifmo.soa.model.*
import java.time.LocalDate

@ApplicationScoped
class OrganizationRepository : PanacheRepositoryBase<OrganizationEntity, Int> {
    fun allModels(): List<Organization> = list("order by id").map(OrganizationEntity::toModel)
    fun modelById(id: Int): Organization? = findById(id)?.toModel()
    fun entityById(id: Int): OrganizationEntity? = findById(id)

    fun create(input: OrganizationInput): Organization {
        val entity = OrganizationEntity().apply {
            name = input.name
            coordinatesX = input.coordinates.x
            coordinatesY = input.coordinates.y
            creationDate = LocalDate.now()
            annualTurnover = input.annualTurnover
            fullName = input.fullName
            type = input.type
            street = input.postalAddress.street
        }
        persist(entity)
        flush()
        return entity.toModel()
    }

    fun update(id: Int, input: OrganizationInput): Organization? {
        val entity = findById(id) ?: return null
        entity.name = input.name
        entity.coordinatesX = input.coordinates.x
        entity.coordinatesY = input.coordinates.y
        entity.annualTurnover = input.annualTurnover
        entity.fullName = input.fullName
        entity.type = input.type
        entity.street = input.postalAddress.street
        flush()
        return entity.toModel()
    }

    fun removeOrganization(id: Int): Boolean {
        val entity = findById(id) ?: return false
        delete(entity)
        return true
    }
}
