package app.naviamp.storage

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeStorageDriverFactoryTest {
    @Test
    fun rejectsRelativeDatabaseDirectory() {
        assertFailsWith<IllegalArgumentException> {
            NativeStorageDriverFactory(StorageDatabaseLocation("Library/Application Support"))
        }
    }
}
