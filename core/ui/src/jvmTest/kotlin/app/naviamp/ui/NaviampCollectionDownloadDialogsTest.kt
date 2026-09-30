package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampCollectionDownloadDialogsTest {
    @Test
    fun artistPreviewShowsEstimateAndRequiresConfirmation() = runComposeUiTest {
        var confirmations = 0
        var dismissals = 0
        setContent {
            NaviampCollectionDownloadDialogs(
                downloads = NaviampDownloadsScreenUi(collectionPreview = NaviampCollectionDownloadPreviewUi(
                    title = "Artist", kind = NaviampCollectionDownloadKind.Artist,
                    albumCount = 2, trackCount = 12, alreadyDownloadedCount = 3,
                    estimatedNewBytes = 100_000_000, remainingBudgetBytes = 200_000_000,
                )),
                actions = actions(onConfirm = { confirmations++ }, onDismiss = { dismissals++ }),
                colors = NaviampColors.Dark,
            )
        }
        onNodeWithText("Keep Artist downloaded?").assertIsDisplayed()
        onNodeWithText("2 albums · 12 tracks · 3 already saved").assertIsDisplayed()
        assertEquals(0, confirmations)
        onNodeWithText("Keep downloaded").performClick()
        assertEquals(1, confirmations)
        onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun storageLimitBlocksConfirmationAndRemovalOffersBothFileChoices() = runComposeUiTest {
        var removalChoice: Boolean? = null
        setContent {
            NaviampCollectionDownloadDialogs(
                downloads = NaviampDownloadsScreenUi(collectionPreview = NaviampCollectionDownloadPreviewUi(
                    title = "Album", kind = NaviampCollectionDownloadKind.Album,
                    error = NaviampCollectionDownloadPreviewError.StorageLimit,
                )),
                actions = actions(onStop = { removalChoice = it }),
                colors = NaviampColors.Dark,
            )
        }
        onNodeWithText("This collection exceeds your download storage budget.").assertIsDisplayed()
        onNodeWithText("Keep downloaded").assertDoesNotExist()

        setContent {
            NaviampCollectionDownloadDialogs(
                downloads = NaviampDownloadsScreenUi(collectionRemoval = NaviampCollectionDownloadRemovalUi(
                    "Album", NaviampCollectionDownloadKind.Album,
                )),
                actions = actions(onStop = { removalChoice = it }),
                colors = NaviampColors.Dark,
            )
        }
        onNodeWithText("Stop keeping Album downloaded?").assertIsDisplayed()
        onNodeWithText("Keep files").performClick()
        assertEquals(false, removalChoice)
        onNodeWithText("Remove unneeded files").performClick()
        assertEquals(true, removalChoice)
    }

    private fun actions(
        onConfirm: () -> Unit = {},
        onStop: (Boolean) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = NaviampDownloadsActions({}, {}, {}, {}, {}, {}, onConfirm, onStop, onDismiss)
}
