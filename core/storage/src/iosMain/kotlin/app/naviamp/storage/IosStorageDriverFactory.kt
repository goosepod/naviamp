package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver

/** Retains the iOS constructor/API while delegating the actual Kotlin/Native SQLite effect. */
class IosStorageDriverFactory(location: StorageDatabaseLocation) {
    private val native = NativeStorageDriverFactory(location)

    fun createDriver(): SqlDriver = native.createDriver()
}
