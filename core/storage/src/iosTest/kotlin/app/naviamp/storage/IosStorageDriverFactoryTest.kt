package app.naviamp.storage

import kotlin.test.Test
import kotlin.test.assertFailsWith

class IosStorageDriverFactoryTest {
    @Test
    fun rejectsRelativeDatabaseDirectory() {
        assertFailsWith<IllegalArgumentException> {
            IosStorageDriverFactory(StorageDatabaseLocation("Library/Application Support"))
        }
    }
}
