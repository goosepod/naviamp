package app.naviamp.android

import android.content.Context

object AndroidAppDependencyStore {
    @Volatile
    private var instance: AndroidAppDependencies? = null

    fun get(context: Context): AndroidAppDependencies =
        instance ?: synchronized(this) {
            instance ?: AndroidAppDependencies(context.applicationContext).also { instance = it }
        }
}
