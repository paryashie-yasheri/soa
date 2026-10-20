package ru.ifmo.soa.repository

import jakarta.persistence.*
import ru.ifmo.soa.model.*
import java.time.LocalDate

@Entity
@Table(name = "organizations", schema = "s389491")
class OrganizationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Int = 0
    @Column(nullable = false, columnDefinition = "text") var name: String = ""
    @Column(name = "coordinates_x", nullable = false) var coordinatesX: Float = 0f
    @Column(name = "coordinates_y", nullable = false) var coordinatesY: Long = 0
    @Column(name = "creation_date", nullable = false) var creationDate: LocalDate = LocalDate.now()
    @Column(name = "annual_turnover") var annualTurnover: Int? = null
    @Column(name = "full_name", columnDefinition = "text") var fullName: String? = null
    @Enumerated(EnumType.STRING) @Column(name = "organization_type", nullable = false) var type: OrganizationType = OrganizationType.COMMERCIAL
    @Column(name = "postal_address_street", nullable = false, length = 158) var street: String = ""
    @OneToMany(mappedBy = "organization", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var employees: MutableList<EmployeeEntity> = mutableListOf()

    fun toModel() = Organization(
        id, name, Coordinates(coordinatesX, coordinatesY),
        creationDate, annualTurnover, fullName, type, Address(street)
    )
}

@Entity
@Table(name = "employees", schema = "s389491")
class EmployeeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Int = 0
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: OrganizationEntity? = null
    @Column(nullable = false, columnDefinition = "text") var name: String = ""
    @Column(name = "job_position", columnDefinition = "text") var position: String? = null
    var salary: Double? = null

    fun toModel() = Employee(id, requireNotNull(organization).id, name, position, salary)
}
