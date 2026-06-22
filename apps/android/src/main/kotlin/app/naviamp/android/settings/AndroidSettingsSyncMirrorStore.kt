package app.naviamp.android

import android.content.Context
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncFileName
import app.naviamp.domain.settings.SettingsSyncJson
import java.io.File

class AndroidSettingsSyncMirrorStore(
    context: Context,
) {
    private val mirrorFile = File(
        File(context.applicationContext.filesDir, MirrorDirectoryName),
        SettingsSyncFileName,
    )

    fun read(): SettingsSyncDocument? {
        if (!mirrorFile.isFile) return null
        return runCatching {
            SettingsSyncJson.decode(mirrorFile.readText())
        }.getOrElse { error("Local settings mirror is not valid Naviamp settings JSON.") }
    }

    fun write(document: SettingsSyncDocument) {
        val parent = mirrorFile.parentFile ?: error("Could not locate local settings mirror folder.")
        if (!parent.exists() && !parent.mkdirs()) {
            error("Could not create local settings mirror folder.")
        }
        val pendingFile = File(parent, "$SettingsSyncFileName.tmp")
        pendingFile.writeText(SettingsSyncJson.encode(document))
        if (!pendingFile.renameTo(mirrorFile)) {
            pendingFile.copyTo(mirrorFile, overwrite = true)
            pendingFile.delete()
        }
    }
}

private const val MirrorDirectoryName = "settings-sync"
