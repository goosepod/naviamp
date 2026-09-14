package app.naviamp.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element

class NaviampTranslationResourcesTest {
    @Test
    fun maintainedTranslationsHaveMatchingKeysTypesAndFormatArguments() {
        val english = resources("values")
        val spanish = resources("values-es")
        assertEquals(english.keys, spanish.keys, "Every maintained language must include each resource")
        english.forEach { (key, source) ->
            val translated = spanish.getValue(key)
            assertEquals(source.tagName, translated.tagName, key)
            val expected = arguments(if (source.tagName == "plurals") {
                (source.getElementsByTagName("item").item(0) as Element).textContent
            } else source.textContent)
            if (translated.tagName == "plurals") {
                val items = translated.getElementsByTagName("item")
                for (index in 0 until items.length) {
                    assertEquals(expected, arguments(items.item(index).textContent), "$key plural $index")
                }
            } else assertEquals(expected, arguments(translated.textContent), key)
        }
    }

    private fun arguments(text: String) = Regex("%[0-9]+\\$[sd]").findAll(text).map { it.value }.sorted().toList()

    private fun resources(folder: String): Map<String, Element> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val nodes = factory.newDocumentBuilder()
            .parse(File("src/commonMain/composeResources/$folder/strings.xml")).documentElement.childNodes
        val result = linkedMapOf<String, Element>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val key = element.getAttribute("name")
            check(result.put(key, element) == null) { "Duplicate resource $key in $folder" }
        }
        return result
    }
}
