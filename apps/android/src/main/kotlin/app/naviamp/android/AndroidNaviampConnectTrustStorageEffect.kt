package app.naviamp.android

import android.content.Context
import app.naviamp.app.NaviampConnectTrustStorageEffect

/** Android durable-string adapter; the trust schema and mutation policy remain in Core. */
class AndroidNaviampConnectTrustStorageEffect(context: Context) : NaviampConnectTrustStorageEffect {
    private val preferences = context.applicationContext.getSharedPreferences(
        "naviamp-connect-trust",
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = preferences.getString(TrustKey, null)

    override fun write(value: String) {
        check(preferences.edit().putString(TrustKey, value).commit()) {
            "Android could not persist Naviamp Connect trust."
        }
    }

    private companion object {
        const val TrustKey = "trusted-devices-v1"
    }
}
