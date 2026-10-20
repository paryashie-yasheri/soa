package ru.ifmo.soa.repository

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import ru.ifmo.soa.model.*
import java.time.LocalDate

@ApplicationScoped
class OrganizationRepository {
    @field:PersistenceContext
    private lateinit var entityManager: EntityManager

    fun allModels(): List<Organization> =
        entityManager.createQuery("select o from OrganizationEntity o order by o.id", OrganizationEntity::class.java)
            .resultList.map { it.toModel() }

    fun modelById(id: Int): Organization? = entityManager.find(OrganizationEntity::class.java, id)?.toModel()
    fun entityById(id: Int): OrganizationEntity? = entityManager.find(OrganizationEntity::class.java, id)

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
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toModel()
    }

    fun update(id: Int, input: OrganizationInput): Organization? {
        val entity = entityManager.find(OrganizationEntity::class.java, id) ?: return null
        entity.name = input.name
        entity.coordinatesX = input.coordinates.x
        entity.coordinatesY = input.coordinates.y
        entity.annualTurnover = input.annualTurnover
        entity.fullName = input.fullName
        entity.type = input.type
        entity.street = input.postalAddress.street
        entityManager.flush()
        return entity.toModel()
    }

    fun removeOrganization(id: Int): Boolean {
        val entity = entityManager.find(OrganizationEntity::class.java, id) ?: return false
        entityManager.remove(entity)
        return true
    }
}
