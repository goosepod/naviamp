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
        for (folder in listOf("values-es", "values-pt-rBR")) {
            val translation = resources(folder)
            assertEquals(english.keys, translation.keys, "Full catalog must include each resource: $folder")
            english.forEach { (key, source) ->
                val translated = translation.getValue(key)
                assertEquals(source.tagName, translated.tagName, "$folder $key")
                val expected = arguments(if (source.tagName == "plurals") {
                    (source.getElementsByTagName("item").item(0) as Element).textContent
                } else source.textContent)
                if (translated.tagName == "plurals") {
                    val items = translated.getElementsByTagName("item")
                    for (index in 0 until items.length) {
                        assertEquals(expected, arguments(items.item(index).textContent), "$folder $key plural $index")
                    }
                } else assertEquals(expected, arguments(translated.textContent), "$folder $key")
            }
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
