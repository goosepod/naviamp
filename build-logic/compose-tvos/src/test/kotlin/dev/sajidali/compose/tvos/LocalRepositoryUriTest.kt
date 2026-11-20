package dev.sajidali.compose.tvos

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalRepositoryUriTest {
    @Test fun relocatedRepositoryWithEscapedCharactersFindsOnlyActualModules() {
        val root = Files.createTempDirectory("tvOS port é # ").toFile()
        try {
            val module = root.resolve("fixture/group/ui/1.0/ui-1.0.module")
            module.parentFile.mkdirs()
            module.writeText("{}")
            assertEquals(TvosVariantDiscovery.ModuleExistence.Exists, TvosVariantDiscovery.probeTargetModuleExistence(
                listOf(root.toURI().toString()), "fixture.group", "ui", "1.0", null))
            assertEquals(TvosVariantDiscovery.ModuleExistence.Absent, TvosVariantDiscovery.probeTargetModuleExistence(
                listOf(root.toURI().toString()), "fixture.group", "missing", "1.0", null))
        } finally { root.deleteRecursively() }
    }
}
