package app.naviamp.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every Connect status shown to people resolves to a source string and a complete translation. */
class NaviampConnectStatusTextTest {
    @Test
    fun everyStatusHasSourceAndCompleteCatalogStrings() {
        for (folder in listOf("values", "values-es", "values-pt-rBR")) {
            val catalog = File("src/commonMain/composeResources/$folder/strings.xml").readText()
            NaviampConnectStatusText.entries.forEach { status ->
                assertTrue(catalog.contains("name=\"${status.resource.key}\""), "$folder is missing ${status.resource.key}")
            }
        }
    }
}
