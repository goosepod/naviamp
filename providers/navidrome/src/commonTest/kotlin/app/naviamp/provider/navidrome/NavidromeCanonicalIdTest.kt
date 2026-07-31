package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun migratesEntityIdsEmbeddedInNavidromeCoverArtKeys() {
        assertEquals(
            "mf-0aZ8vPOn4jcLgReUJu3nBG_6a25e6f3",
            NavidromeCanonicalId.migrate("mf-bdlbRXwqlTrUYWaYnATlLf_6a25e6f3"),
        )
        assertEquals(
            "al-0aZ8vPOn4jcLgReUJu3nBG_revision",
            NavidromeCanonicalId.migrate("al-bdlbRXwqlTrUYWaYnATlLf_revision"),
        )
    }

}
