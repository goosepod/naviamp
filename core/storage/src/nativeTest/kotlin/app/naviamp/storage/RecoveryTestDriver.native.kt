package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import co.touchlab.sqliter.DatabaseConfiguration

actual fun recoveryTestDriver(): SqlDriver = inMemoryDriver(NaviampStorageDatabase.Schema,
    DatabaseConfiguration.Extended(foreignKeyConstraints = true))
