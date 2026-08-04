package app.naviamp.app

import app.naviamp.domain.settings.SettingsSyncDocument

/** Narrow file boundary for a selected settings-sync document location. */
interface NaviampSettingsSyncDocumentStore {
    val displayName: String

    fun read(): SettingsSyncDocument?

    fun write(document: SettingsSyncDocument)
}
