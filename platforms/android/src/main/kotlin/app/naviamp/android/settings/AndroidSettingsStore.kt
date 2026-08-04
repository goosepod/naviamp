package app.naviamp.android

import android.content.Context
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration

/** SharedPreferences effect for Android's persisted document-tree URI. */
class AndroidSettingsStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AndroidSettingsPreferencesName,
        Context.MODE_PRIVATE,
    )

    fun loadSettingsSync(): NaviampCoreSettingsSyncConfiguration =
        NaviampCoreSettingsSyncConfiguration(
            directoryPath = preferences.getString(KeySettingsSyncTreeUri, null),
            autoExportEnabled = preferences.getBoolean(KeySettingsSyncAutoExportEnabled, false),
        )

    fun saveSettingsSync(configuration: NaviampCoreSettingsSyncConfiguration) {
        preferences.edit()
            .putString(KeySettingsSyncTreeUri, configuration.directoryPath)
            .putBoolean(KeySettingsSyncAutoExportEnabled, configuration.autoExportEnabled)
            .apply()
    }
}

private const val KeySettingsSyncTreeUri = "settings_sync_tree_uri"
private const val KeySettingsSyncAutoExportEnabled = "settings_sync_auto_export_enabled"
