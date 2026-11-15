package app.naviamp.bootstrap

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import co.touchlab.sqliter.DatabaseConfiguration
import app.naviamp.storage.NaviampStorageDatabase

actual fun storedApplicationTestDriver(): SqlDriver = inMemoryDriver(NaviampStorageDatabase.Schema,
    DatabaseConfiguration.Extended(foreignKeyConstraints = true))
