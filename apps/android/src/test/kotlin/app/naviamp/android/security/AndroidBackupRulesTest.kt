package app.naviamp.android.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidBackupRulesTest {
    @Test
    fun manifestUsesCredentialAwareBackupRules() {
        val manifest = androidSourceFile("AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }

    @Test
    fun cloudAndDeviceTransferExcludeCredentialsAndDatabase() {
        val legacyRules = androidSourceFile("res/xml/backup_rules.xml").readText()
        val currentRules = androidSourceFile("res/xml/data_extraction_rules.xml").readText()

        listOf(legacyRules, currentRules).forEach { rules ->
            assertTrue(rules.contains("domain=\"database\" path=\".\""))
            assertTrue(rules.contains("domain=\"sharedpref\" path=\"naviamp_android_credentials.xml\""))
        }
        assertTrue(currentRules.contains("<cloud-backup>"))
        assertTrue(currentRules.contains("<device-transfer>"))
    }

    private fun androidSourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/$relativePath"),
            File("apps/android/src/main/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Could not locate Android source file: $relativePath")
    }
}
