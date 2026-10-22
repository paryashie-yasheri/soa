package ru.ifmo.soa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.resource.XmlCodec
import ru.ifmo.soa.model.*
import java.time.LocalDate

class XmlCodecTest {
    private fun organization(turnover: String, name: String = "<name>Acme</name>") =
        """<organization xmlns:nil="http://www.w3.org/2001/XMLSchema-instance">$name<coordinates><x>1.5</x><y>2</y></coordinates>$turnover<type>COMMERCIAL</type><postalAddress><street>Main</street></postalAddress></organization>"""

    @Test
    fun `nullable fields accept XML Schema nil with any namespace prefix`() {
        for (nil in listOf("true", "1")) {
            val input = XmlCodec.organizationInput(organization("""<annualTurnover nil:nil="$nil"/>"""))
            assertNull(input.annualTurnover)
            val employee = XmlCodec.employeeInput("""<employee xmlns:n="http://www.w3.org/2001/XMLSchema-instance"><name>Ada</name><salary n:nil="$nil"/></employee>""")
            assertNull(employee.salary)
        }
    }

    @Test
    fun `required fields reject explicit null even when text is supplied`() {
        assertThrows(IllegalArgumentException::class.java) {
            XmlCodec.organizationInput(organization("", """<name nil:nil="true">Acme</name>"""))
        }
    }

    @Test
    fun `false nil preserves a value and unrelated namespaces are not null`() {
        assertEquals(42, XmlCodec.organizationInput(organization("""<annualTurnover nil:nil="false">42</annualTurnover>""")).annualTurnover)
        assertEquals(42, XmlCodec.organizationInput(organization("""<annualTurnover xmlns:other="urn:other" other:nil="true">42</annualTurnover>""")).annualTurnover)
    }

    @Test
    fun `DOCTYPE declarations remain forbidden`() {
        assertThrows(org.xml.sax.SAXException::class.java) {
            XmlCodec.organizationInput("<!DOCTYPE organization [<!ENTITY name 'Acme'>]>" + organization(""))
        }
    }

    @Test
    fun `XML input rejects wrong roots malformed numbers and oversized payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            XmlCodec.employeeInput("<employees><name>Ada</name></employees>")
        }
        assertThrows(IllegalArgumentException::class.java) {
            XmlCodec.employeeInput("<employee><name>Ada</name><salary>NaN!</salary></employee>")
        }
        assertThrows(IllegalArgumentException::class.java) {
            XmlCodec.organizationInput(organization("", "<name>" + "n".repeat(66_000) + "</name>"))
        }
    }

    @Test
    fun `employee nullable fields preserve omission and parse ordinary values`() {
        val parsed = XmlCodec.employeeInput("<employee><name>Ada &amp; Grace</name><position>Engineer</position><salary>0.0</salary></employee>")
        assertEquals(EmployeeInput("Ada & Grace", "Engineer", 0.0), parsed)
        assertEquals(EmployeeInput("Ada", null, null), XmlCodec.employeeInput("<employee><name>Ada</name></employee>"))
        assertEquals("<employee id=\"4\" organizationId=\"9\"><name>Ada &amp; Grace</name><position>Engineer</position><salary>0.0</salary></employee>",
            XmlCodec.employee(Employee(4, 9, "Ada & Grace", "Engineer", 0.0)))
        assertEquals("<employee id=\"4\" organizationId=\"9\"><name>Ada</name></employee>",
            XmlCodec.employee(Employee(4, 9, "Ada", null, null)))
    }

    @Test
    fun `organization serialization escapes XML metacharacters in all text fields`() {
        val xml = XmlCodec.organization(Organization(1, "A & <B>", Coordinates(1f, 2), LocalDate.parse("2024-01-01"),
            1, "\"quoted\"", OrganizationType.TRUST, Address("R&D's <road>")))
        assertTrue(xml.contains("<name>A &amp; &lt;B&gt;</name>"))
        assertTrue(xml.contains("<fullName>&quot;quoted&quot;</fullName>"))
        assertTrue(xml.contains("<street>R&amp;D&apos;s &lt;road&gt;</street>"))
    }
}
