package app.naviamp.bootstrap

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.storage.NaviampStorageDatabase

actual fun storedApplicationTestDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
    NaviampStorageDatabase.Schema.create(it)
}
