package ru.ifmo.soa.resource

import org.w3c.dom.Element
import ru.ifmo.soa.model.*
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object XmlCodec {
    fun organizationInput(xml: String): OrganizationInput {
        val root = parse(xml)
        require(root.tagName == "organization") { "Ожидался XML-элемент organization" }
        val coordinates = root.child("coordinates")
            ?: throw IllegalArgumentException("coordinates обязателен")
        val address = root.child("postalAddress")
            ?: throw IllegalArgumentException("postalAddress обязателен")
        val turnover = root.optional("annualTurnover")?.trim()?.toInt()
        val type = OrganizationType.valueOf(root.text("type"))
        val x = coordinates.text("x").trim().toFloat()
        val y = coordinates.text("y").trim().toLong()
        return OrganizationInput(
            root.text("name"),
            Coordinates(x, y),
            turnover,
            root.optional("fullName"),
            type,
            Address(address.text("street"))
        )
    }

    fun employeeInput(xml: String): EmployeeInput {
        val root = parse(xml)
        require(root.tagName == "employee") { "Ожидался XML-элемент employee" }
        val salary = root.optional("salary")?.trim()?.toDouble()
        return EmployeeInput(root.text("name"), root.optional("position"), salary)
    }

    fun organization(o: Organization): String = buildString {
        append("<organization id=\"").append(o.id).append("\">")
        append("<name>").append(esc(o.name)).append("</name>")
        append("<coordinates>")
        append("<x>").append(o.coordinates.x).append("</x>")
        append("<y>").append(o.coordinates.y).append("</y>")
        append("</coordinates>")
        append("<creationDate>").append(o.creationDate).append("</creationDate>")
        o.annualTurnover?.let { append("<annualTurnover>").append(it).append("</annualTurnover>") }
        o.fullName?.let { append("<fullName>").append(esc(it)).append("</fullName>") }
        append("<type>").append(o.type).append("</type>")
        append("<postalAddress><street>").append(esc(o.postalAddress.street)).append("</street></postalAddress>")
        append("</organization>")
    }

    fun organizations(page: OrganizationPage): String = buildString {
        append("<organizations>")
        append("<total>").append(page.total).append("</total>")
        append("<page>").append(page.page).append("</page>")
        append("<size>").append(page.size).append("</size>")
        page.items.forEach { append(organization(it)) }
        append("</organizations>")
    }

    fun organizations(items: List<Organization>): String = buildString {
        append("<organizations><total>").append(items.size).append("</total>")
        items.forEach { append(organization(it)) }
        append("</organizations>")
    }

    fun employee(e: Employee): String = buildString {
        append("<employee id=\"").append(e.id).append("\" organizationId=\"").append(e.organizationId).append("\">")
        append("<name>").append(esc(e.name)).append("</name>")
        e.position?.let { append("<position>").append(esc(it)).append("</position>") }
        e.salary?.let { append("<salary>").append(it).append("</salary>") }
        append("</employee>")
    }

    fun employees(items: List<Employee>): String = buildString {
        append("<employees>")
        items.forEach { append(employee(it)) }
        append("</employees>")
    }

    fun groups(items: Map<String, Int>): String = buildString {
        append("<groups>")
        items.entries.forEach { (name, count) ->
            append("<group><name>").append(esc(name)).append("</name><count>").append(count).append("</count></group>")
        }
        append("</groups>")
    }

    fun count(n: Long): String = "<count>$n</count>"

    fun uniqueTurnovers(items: List<Int?>): String = buildString {
        append("<values xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">")
        items.forEach { value ->
            if (value != null) append("<value>").append(value).append("</value>")
            else append("<value xsi:nil=\"true\"/>")
        }
        append("</values>")
    }

    private fun parse(xml: String): Element {
        require(xml.toByteArray().size <= 65_536) { "XML слишком большой" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun Element.child(tag: String): Element? =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .firstOrNull { it.tagName == tag }

    private fun Element.text(tag: String): String {
        val element = child(tag) ?: throw IllegalArgumentException("$tag обязателен")
        require(!element.nil()) { "$tag не может быть null" }
        return element.textContent
    }

    private fun Element.optional(tag: String): String? =
        child(tag)?.let { if (it.nil()) null else it.textContent }

    private fun Element.nil(): Boolean =
        getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil") in setOf("true", "1")

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
