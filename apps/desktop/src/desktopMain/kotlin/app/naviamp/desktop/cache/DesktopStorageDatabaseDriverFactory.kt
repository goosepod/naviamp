package app.naviamp.desktop

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** JVM host adapter for Desktop's SQLite driver and connection-level configuration. */
internal object DesktopStorageDatabaseDriverFactory : StorageDatabaseDriverFactory {
    override fun create(location: StorageDatabaseLocation): SqlDriver {
        val directory = Path.of(location.directoryPath).toAbsolutePath()
        Files.createDirectories(directory)
        Class.forName("org.sqlite.JDBC")
        val properties = Properties().apply {
            setProperty("busy_timeout", SqliteBusyTimeoutMillis.toString())
            setProperty("journal_mode", "WAL")
        }
        return JdbcSqliteDriver(
            url = "jdbc:sqlite:${directory.resolve(location.fileName)}",
            properties = properties,
        )
    }
}

private const val SqliteBusyTimeoutMillis = 10_000
