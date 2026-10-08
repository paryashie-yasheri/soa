package ru.ifmo.soa.model

import java.time.LocalDate

enum class OrganizationType { COMMERCIAL, TRUST, PRIVATE_LIMITED_COMPANY, OPEN_JOINT_STOCK_COMPANY }

data class Coordinates(val x: Float, val y: Long)

data class Address(val street: String)

data class OrganizationInput(
    val name: String,
    val coordinates: Coordinates,
    val annualTurnover: Int?,
    val fullName: String?,
    val type: OrganizationType,
    val postalAddress: Address
)

data class Organization(
    val id: Int,
    val name: String,
    val coordinates: Coordinates,
    val creationDate: LocalDate,
    val annualTurnover: Int?,
    val fullName: String?,
    val type: OrganizationType,
    val postalAddress: Address
)

data class EmployeeInput(val name: String, val position: String?, val salary: Double?)

data class Employee(
    val id: Int,
    val organizationId: Int,
    val name: String,
    val position: String?,
    val salary: Double?
)

data class OrganizationFilter(
    val id: Int? = null,
    val name: String? = null,
    val coordinatesX: Float? = null,
    val coordinatesY: Long? = null,
    val creationDate: LocalDate? = null,
    val annualTurnover: Int? = null,
    val fullName: String? = null,
    val type: OrganizationType? = null,
    val street: String? = null
)

data class OrganizationPage(val total: Int, val page: Int, val size: Int, val items: List<Organization>)