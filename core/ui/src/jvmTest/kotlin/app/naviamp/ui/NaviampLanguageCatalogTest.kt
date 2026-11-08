package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.domain.settings.InterfaceSettings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampLanguageCatalogTest {
    private val resources = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "core/ui/src/commonMain/composeResources") }.first { it.isDirectory }

    private fun strings(directory: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(directory, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test fun everyBundledCatalogHasASelectableLanguageAndEveryOptionHasResources() {
        val catalogs = resources.listFiles()!!.filter { it.isDirectory && it.name.startsWith("values") }
        val tags = catalogs.map { if (it.name == "values") "en" else it.name.removePrefix("values-").replace("-r", "-") }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(tags.toSet(), InterfaceLanguage.entries.mapNotNull { it.languageTag }.toSet())
        val newKeys = strings(File(resources, "values")).keys.filter {
            it.startsWith("settings_language_") && (it.endsWith("_title") || it == "settings_language_use_subtitle") &&
                it !in setOf("settings_language_title", "settings_language_system_title", "settings_language_english_title", "settings_language_spanish_title")
        }
        catalogs.forEach { directory ->
            val keys = strings(directory).keys
            assertEquals(emptySet(), newKeys.toSet() - keys, directory.name)
        }
    }

    @Test fun theActualLanguageSettingsScrollsToAndSelectsEveryBundledLanguage() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            runComposeUiTest {
                var selected: InterfaceLanguage? = null
                setContent {
                    MaterialTheme {
                        Column(Modifier.width(720.dp).height(500.dp).verticalScroll(rememberScrollState())) {
                            NaviampLanguageSettingsSection(NaviampColors.Dark, InterfaceSettings(),
                                naviampLanguagePack(InterfaceLanguage.English), { selected = it.language })
                        }
                    }
                }
                val labels = strings(File(resources, "values"))
                InterfaceLanguage.entries.forEach { language ->
                    val key = language.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
                    onNodeWithText(labels.getValue("settings_language_${key}_title")).performScrollTo().performClick()
                    runOnIdle { assertEquals(language, selected) }
                }
            }
        } finally { Locale.setDefault(original) }
    }

    @Test fun systemDefaultRestoresTheHostsLayoutDirection() = runComposeUiTest {
        var language by mutableStateOf(InterfaceLanguage.English)
        var direction: LayoutDirection? = null
        val effect = object : NaviampLocaleEffect { override fun applyLanguage(languageTag: String?) = Unit }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NaviampLocaleEnvironment(language, effect) {
                    direction = LocalLayoutDirection.current
                    Text("direction")
                }
            }
        }
        runOnIdle { assertEquals(LayoutDirection.Ltr, direction); language = InterfaceLanguage.System }
        runOnIdle { assertEquals(LayoutDirection.Rtl, direction) }
    }
}
