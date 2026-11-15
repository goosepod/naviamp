package app.naviamp.bootstrap

import app.naviamp.presentation.NaviampCoreMutableSettingsValueStore
import app.naviamp.storage.StorageRecoveryState

/** The complete existing settings catalog uses the same bounded document as connection recovery. */
class NaviampRecoverySettingsValueStore(val recovery: StorageRecoveryState) : NaviampCoreMutableSettingsValueStore {
    override fun read(key: String): String? = recovery.read(key)
    override fun write(key: String, value: String) = recovery.write(key, value)
    override fun remove(key: String) = recovery.remove(key)
}
