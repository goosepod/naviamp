package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionFormMusicFoldersTest {
    @Test
    fun firstReturnedFolderIsMarkedAsDefaultCandidate() {
        val folders = connectionFormMusicFolders(
            listOf(
                "1" to "Music Library",
                "2" to "Classical",
            ),
        )

        assertEquals(
            listOf(
                ConnectionFormMusicFolder(id = "1", name = "Music Library", defaultSelected = true),
                ConnectionFormMusicFolder(id = "2", name = "Classical", defaultSelected = false),
            ),
            folders,
        )
    }

    @Test
    fun defaultSelectionOnlyAppliesWhenNoLibraryIsSelected() {
        val folders = connectionFormMusicFolders(
            listOf(
                "1" to "Music Library",
                "2" to "Classical",
            ),
        )

        assertEquals(listOf("1"), defaultSelectedMusicFolderIds(emptyList(), folders))
        assertEquals(listOf("2"), defaultSelectedMusicFolderIds(listOf("2"), folders))
        assertEquals(emptyList(), defaultSelectedMusicFolderIds(emptyList(), emptyList()))
    }

    @Test
    fun toggleKeepsAtLeastOneSelectedWhenRequired() {
        assertEquals(
            listOf("1"),
            listOf("1").toggleSelectedMusicFolderId(id = "1", requireOne = true),
        )
        assertEquals(
            listOf("2"),
            listOf("1", "2").toggleSelectedMusicFolderId(id = "1", requireOne = true),
        )
        assertEquals(
            emptyList(),
            listOf("1").toggleSelectedMusicFolderId(id = "1", requireOne = false),
        )
    }

    @Test
    fun toggleAddsNewLibraryOnce() {
        assertEquals(
            listOf("1", "2"),
            listOf("1").toggleSelectedMusicFolderId(id = "2", requireOne = true),
        )
        assertEquals(
            listOf("1", "2"),
            listOf("1", "2").toggleSelectedMusicFolderId(id = "2", requireOne = true)
                .toggleSelectedMusicFolderId(id = "2", requireOne = true),
        )
    }

    @Test
    fun summaryUsesNamesWithoutExposingUnknownInternalIds() {
        val folders = connectionFormMusicFolders(
            listOf(
                "1" to "Music Library",
                "2" to "Classical",
            ),
        )

        assertEquals(
            "Music Library",
            selectedMusicFolderSummary(listOf("1", "3"), folders),
        )
        assertEquals(
            "All accessible libraries",
            selectedMusicFolderSummary(emptyList(), folders),
        )
    }
}
