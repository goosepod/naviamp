package app.naviamp.bootstrap

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.naviamp.storage.NaviampStorageDatabase

actual fun storedApplicationTestDriver(): SqlDriver = NativeSqliteDriver(NaviampStorageDatabase.Schema, ":memory:")
