package app.naviamp.android

import android.content.Context
import app.naviamp.presentation.NaviampCoreSettingsValueStore

/**
 * SharedPreferences effect for Core's device-local identity values.
 *
 * Android Auto Backup and device transfer copy SharedPreferences files to other devices, and
 * exclusion works per file. This file is excluded in `backup_rules.xml` and
 * `data_extraction_rules.xml` so a restored or transferred installation never inherits another
 * device's identity. Core owns the keys, generation, and validation.
 */
class AndroidDeviceIdentityValueStore(context: Context) : NaviampCoreSettingsValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        AndroidDeviceIdentityPreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

internal const val AndroidDeviceIdentityPreferencesName = "naviamp_android_device_identity"
