package app.naviamp.app

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

internal const val UPNP_DEVICE_NS = "urn:schemas-upnp-org:device-1-0"
internal const val UPNP_SERVICE_NS = "urn:schemas-upnp-org:service-1-0"
internal const val UPNP_SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
internal const val UPNP_EVENT_NS = "urn:schemas-upnp-org:event-1-0"

class NaviampUpnpProtocolException : Exception("Invalid or unsupported UPnP message")

internal fun upnpCheck(valid: Boolean) {
    if (!valid) throw NaviampUpnpProtocolException()
}

internal class UpnpXmlNode(val namespace: String, val name: String, val attributes: Map<Pair<String, String>, String>) {
    val children = mutableListOf<UpnpXmlNode>()
    val text = StringBuilder()
    fun elements(namespace: String, name: String) = children.filter { it.namespace == namespace && it.name == name }
    fun optional(namespace: String, name: String): UpnpXmlNode? = elements(namespace, name).also { upnpCheck(it.size <= 1) }.firstOrNull()
    fun required(namespace: String, name: String): UpnpXmlNode = optional(namespace, name) ?: throw NaviampUpnpProtocolException()
    fun value(): String { upnpCheck(children.isEmpty()); return text.toString() }
}

/** Same generic parser on every host. No DTD/entity declarations ever reach the parser. */
internal fun parseUpnpXml(xml: String): UpnpXmlNode {
    upnpCheck(xml.length <= 262_144 && !xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
    val reader = xmlStreaming.newGenericReader(xml, expandEntities = true)
    try {
        val stack = ArrayDeque<UpnpXmlNode>()
        var root: UpnpXmlNode? = null
        var nodes = 0
        var events = 0
        var textCount = 0
        while (reader.hasNext()) {
            upnpCheck(++events <= 32_768)
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    upnpCheck(stack.size < 32 && ++nodes <= 4_096 && reader.attributeCount <= 32 && reader.namespaceDecls.size <= 32)
                    val attributes = buildMap {
                        for (index in 0 until reader.attributeCount) {
                            val key = reader.getAttributeNamespace(index) to reader.getAttributeLocalName(index)
                            upnpCheck(key !in this)
                            put(key, reader.getAttributeValue(index))
                        }
                    }
                    val node = UpnpXmlNode(reader.namespaceURI, reader.localName, attributes)
                    if (stack.isEmpty()) { upnpCheck(root == null); root = node } else stack.last().children += node
                    stack.addLast(node)
                }
                EventType.END_ELEMENT -> { upnpCheck(stack.isNotEmpty()); stack.removeLast() }
                EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE -> {
                    val text = reader.text
                    textCount += text.length
                    upnpCheck(textCount <= 262_144)
                    if (stack.isEmpty()) upnpCheck(text.isBlank()) else stack.last().text.append(text)
                }
                EventType.DOCDECL, EventType.ENTITY_REF -> throw NaviampUpnpProtocolException()
                else -> Unit
            }
        }
        upnpCheck(stack.isEmpty())
        return root ?: throw NaviampUpnpProtocolException()
    } catch (failure: NaviampUpnpProtocolException) {
        throw failure
    } catch (_: Exception) {
        // Parser diagnostics can contain device-supplied URLs/tokens. Keep them out of status/logs.
        throw NaviampUpnpProtocolException()
    } finally {
        reader.close()
    }
}

internal fun upnpXmlText(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val char = value[index++]
        when {
            char.isHighSurrogate() -> {
                upnpCheck(index < value.length && value[index].isLowSurrogate())
                append(char); append(value[index++])
            }
            char.isLowSurrogate() || char == '\uFFFE' || char == '\uFFFF' || (char.code < 32 && char !in "\t\r\n") ->
                throw NaviampUpnpProtocolException()
            else -> append(when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> char.toString()
            })
        }
    }
}
