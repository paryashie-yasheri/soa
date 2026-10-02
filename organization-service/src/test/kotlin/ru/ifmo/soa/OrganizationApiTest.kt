package ru.ifmo.soa

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class OrganizationApiTest {
    @BeforeEach
    fun clearCollection() {
        val xml = given().accept("application/xml").get("/organizations?size=100").then().statusCode(200).extract().asString()
        Regex("<organization id=\"(\\d+)\"").findAll(xml).forEach { match ->
            given().delete("/organizations/${match.groupValues[1]}").then().statusCode(204)
        }
    }

    @Test
    fun `organization CRUD persists generated fields and rejects invalid payloads`() {
        val invalid = "<organization><name></name><coordinates><x>1</x><y>2</y></coordinates><type>COMMERCIAL</type><postalAddress><street>Main</street></postalAddress></organization>"
        given().contentType("application/xml").body(invalid).post("/organizations").then().statusCode(400).body(containsString("name"))

        val created = given().contentType("application/xml").body(orgXml("Before update", 15)).post("/organizations")
            .then().statusCode(201).extract().asString()
        val id = Regex("id=\"(\\d+)\"").find(created)!!.groupValues[1]
        val creationDate = Regex("<creationDate>([^<]+)</creationDate>").find(created)!!.groupValues[1]

        given().get("/organizations/$id").then().statusCode(200)
            .body(containsString("<name>Before update</name>"), containsString("<creationDate>$creationDate</creationDate>"))

        val updated = given().contentType("application/xml").body(orgXml("After update", 25)).put("/organizations/$id")
            .then().statusCode(200).extract().asString()
        assertTrue(updated.contains("<name>After update</name>"))
        assertTrue(updated.contains("<creationDate>$creationDate</creationDate>"))

        given().contentType("application/xml").body(orgXml("Bad address", 1, "x".repeat(159)))
            .put("/organizations/$id").then().statusCode(400).body(containsString("158"))
        given().delete("/organizations/$id").then().statusCode(204)
        given().get("/organizations/$id").then().statusCode(404)
    }

    @Test
    fun `collection supports field filters sorting and paging`() {
        given().contentType("application/xml").body(orgXml("Filter A", 30)).post("/organizations").then().statusCode(201)
        given().contentType("application/xml").body(orgXml("Filter B", 10)).post("/organizations").then().statusCode(201)
        given().contentType("application/xml").body(orgXml("Filter C", 20)).post("/organizations").then().statusCode(201)

        val filtered = given().queryParam("name", "Filter B").get("/organizations").then().statusCode(200).extract().asString()
        assertTrue(filtered.contains("<total>1</total>"))
        assertTrue(filtered.contains("<name>Filter B</name>"))

        val page = given().queryParam("sortBy", "annualTurnover").queryParam("sortOrder", "asc")
            .queryParam("page", 2).queryParam("size", 1).get("/organizations").then().statusCode(200).extract().asString()
        assertTrue(page.contains("<total>3</total>"))
        assertTrue(page.contains("<annualTurnover>20</annualTurnover>"))
        assertTrue(page.contains("<page>2</page>"))
    }

    @Test
    fun `employee and aggregation resources use the database records`() {
        val first = createOrg("Same name", 100)
        val second = createOrg("Same name", 200)
        val employeeXml = "<employee><name>Ada Lovelace</name><position>Engineer</position><salary>5000</salary></employee>"
        val createdEmployee = given().contentType("application/xml").body(employeeXml).post("/organizations/$first/employees")
            .then().statusCode(201).extract().asString()
        val employeeId = Regex("id=\"(\\d+)\"").find(createdEmployee)!!.groupValues[1]

        val employees = given().get("/organizations/$first/employees").then().statusCode(200).extract().asString()
        assertTrue(employees.contains("Ada Lovelace"))
        given().delete("/organizations/$first/employees/$employeeId").then().statusCode(204)
        assertTrue(!given().get("/organizations/$first/employees").then().statusCode(200).extract().asString().contains("Ada Lovelace"))

        val groups = given().get("/organizations/stats/grouped-by-name").then().statusCode(200).extract().asString()
        assertTrue(groups.contains("<name>Same name</name><count>2</count>"))
        val less = given().get("/organizations/stats/annual-turnover/less-than/150").then().statusCode(200).extract().asString()
        assertTrue(less.contains("<count>1</count>"))
        val unique = given().get("/organizations/stats/annual-turnover/unique").then().statusCode(200).extract().asString()
        assertTrue(unique.contains("<value>100</value>") && unique.contains("<value>200</value>"))
        assertTrue(second > first)
    }

    private fun createOrg(name: String, turnover: Int): Int {
        val body = given().contentType("application/xml").body(orgXml(name, turnover)).post("/organizations").then().statusCode(201).extract().asString()
        return Regex("id=\"(\\d+)\"").find(body)!!.groupValues[1].toInt()
    }

    private fun orgXml(name: String, turnover: Int, street: String = "Main street") =
        "<organization><name>$name</name><coordinates><x>1.5</x><y>2</y></coordinates><annualTurnover>$turnover</annualTurnover><type>COMMERCIAL</type><postalAddress><street>$street</street></postalAddress></organization>"
}
