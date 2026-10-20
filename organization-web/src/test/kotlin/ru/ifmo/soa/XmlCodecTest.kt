package ru.ifmo.soa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ifmo.soa.resource.XmlCodec

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
}
