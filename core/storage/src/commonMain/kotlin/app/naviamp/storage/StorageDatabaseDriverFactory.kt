package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver

/** Platform boundary for opening a SQLDelight driver at a host-selected location. */
fun interface StorageDatabaseDriverFactory {
    fun create(location: StorageDatabaseLocation): SqlDriver
}
