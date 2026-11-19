package app.naviamp.ios

import app.naviamp.app.NaviampApplicationRuntimeOwner
import app.naviamp.storage.StorageCredentialProtector

/** Native iOS resource factory; the common owner controls reuse and shutdown. Call on the main actor. */
class NaviampIosApplicationOwner(
    applicationSupportDirectory: String,
    credentialProtector: StorageCredentialProtector,
) {
    private val owner = NaviampApplicationRuntimeOwner(
        create = { NaviampIosApplication(applicationSupportDirectory, credentialProtector) },
        dispose = NaviampIosApplication::close,
    )

    fun application(): NaviampIosApplication = owner.getOrCreate()

    fun close() = owner.close()
}
