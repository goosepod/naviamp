package app.naviamp.desktop.settings

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncFileName
import app.naviamp.domain.settings.SettingsSyncJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

object DesktopSettingsSyncFile {
    fun syncFile(directory: Path): Path =
        directory.resolve(SettingsSyncFileName)

    fun prepareDirectory(directory: Path): Path {
        Files.createDirectories(directory)
        require(directory.exists() && directory.isDirectory()) {
            "Settings sync location must be a directory."
        }
        return directory
    }

    fun read(directory: Path): SettingsSyncDocument? {
        val file = syncFile(directory)
        if (!file.exists()) return null
        return SettingsSyncJson.decode(file.readText())
    }

    fun write(directory: Path, document: SettingsSyncDocument) {
        val prepared = prepareDirectory(directory)
        syncFile(prepared).writeText(SettingsSyncJson.encode(document))
    }
}
