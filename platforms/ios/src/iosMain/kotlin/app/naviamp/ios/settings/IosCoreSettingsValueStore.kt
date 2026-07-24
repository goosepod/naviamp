package app.naviamp.ios.settings

import app.naviamp.presentation.NaviampCoreSettingsValueStore
import platform.Foundation.NSUserDefaults

/** NSUserDefaults string effect; Core owns keys, serialization, defaults, and normalization. */
class IosCoreSettingsValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NaviampCoreSettingsValueStore {
    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
