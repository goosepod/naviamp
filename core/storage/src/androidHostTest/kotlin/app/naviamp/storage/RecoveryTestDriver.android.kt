package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual fun recoveryTestDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
    NaviampStorageDatabase.Schema.create(it)
    it.execute(null, "PRAGMA foreign_keys=ON", 0)
}
