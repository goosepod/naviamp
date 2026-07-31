package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavidromeCanonicalIdTest {
    @Test
    fun matchesNavidromeMigrationGoldenValues() {
        assertEquals("3LyqmwQBm5IRqlVjNYASwb", NavidromeCanonicalId.migrate("zzzzzzzzzzzzzzzzzzzzzz"))
        assertEquals("6VHl3uR4kss6sUPKA8Cwnk", NavidromeCanonicalId.migrate("e3b7fc2ae9447bbec37a13bf916e3cf6"))
        assertEquals("7rke2SAWaicSeSYzkhww6R", NavidromeCanonicalId.migrate("f47ac10b-58cc-4372-a567-0e02b2c3d479"))
        assertEquals("7N42dgm5tFLK9N8MT7fHC7", NavidromeCanonicalId.migrate("ffffffffffffffffffffffffffffffff"))
    }

    @Test
    fun leavesCanonicalAndUnrecognizedValuesAloneAndIsIdempotent() {
        val values = listOf(
            "5cLJPkLA5DK2BADhoeotPk",
            "!!!!!!!!!!!!!!!!!!!!!!",
            "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
            "favorites",
            "",
        )
        values.forEach { value ->
            val migrated = NavidromeCanonicalId.migrate(value)
            assertEquals(value, migrated)
            assertEquals(migrated, NavidromeCanonicalId.migrate(migrated))
        }
    }

    @Test
    fun activatesOnlyForTheAffectedReleaseLine() {
        assertFalse(navidromeUsesCanonicalIds(null))
        assertFalse(navidromeUsesCanonicalIds("0.63.2 (abc123)"))
        assertFalse(navidromeUsesCanonicalIds("0.63.2-SNAPSHOT (develop/d61a6703)"))
        assertTrue(navidromeUsesCanonicalIds("0.63.2-SNAPSHOT (pr-5824/d61a6703)"))
        assertTrue(navidromeUsesCanonicalIds("0.64.0"))
        assertTrue(navidromeUsesCanonicalIds("v0.64.1-SNAPSHOT"))
        assertTrue(navidromeUsesCanonicalIds("1.0.0"))
    }
}
