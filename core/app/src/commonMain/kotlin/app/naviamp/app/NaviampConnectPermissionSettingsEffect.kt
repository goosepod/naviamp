package app.naviamp.app

/** Opens the native application permission settings; true means opened, not permission granted. */
fun interface NaviampConnectPermissionSettingsEffect {
    fun open(): Boolean
}
