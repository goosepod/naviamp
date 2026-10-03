package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same remote navigation checks run in Desktop Compose and an Android TV activity. */
@OptIn(ExperimentalTestApi::class)
class NaviampAccountSwitcherRenderingTest {
    private val alice = NaviampSavedConnectionUi("alice", "Family music", "https://music.example", "Alice", current = true)
    private val bob = NaviampSavedConnectionUi("bob", "Family music", "https://music.example", "Bob")
    private fun settings(accounts: List<NaviampSavedConnectionUi> = listOf(alice, bob)) =
        NaviampConnectionSettingsUi(
            connection = NaviampShellConnectionUi(connected = true, savedConnections = accounts),
            accountSwitcher = NaviampAccountSwitcherUi(visible = true),
        )
    private fun actions(switch: (NaviampSavedConnectionUi) -> Unit = {}, add: () -> Unit = {}, close: () -> Unit = {}) =
        NaviampConnectionSettingsActions({}, {}, {}, {}, {}, {}, {}, {},
            onSwitchAccount = switch, onAddAccount = add, onCloseAccounts = close)

    @Test
    fun remoteStartsOnCurrentAccountAndSelectsTheNextOne() = runComposeUiTest {
        var selected: String? = null
        setContent { NaviampAccountSwitcher(settings(), actions(switch = { selected = it.id }), NaviampColors.Dark) }
        onNodeWithTag(naviampAccountCardTag("alice")).assertIsFocused().assertIsSelected()
            .performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(naviampAccountCardTag("bob")).assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals("bob", selected) }
    }

    @Test
    fun remoteCanReachAddAccountWithoutSelectingAnExistingAccount() = runComposeUiTest {
        var added = 0
        setContent { NaviampAccountSwitcher(settings(listOf(alice)), actions(add = { added++ }), NaviampColors.Dark) }
        onNodeWithTag(naviampAccountCardTag("alice")).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(NaviampAccountAddTag).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(1, added) }
    }

    @Test
    fun failedSwitchRestoresFocusAndAllowsRetryWhileCurrentAccountStaysMarked() = runComposeUiTest {
        val state = mutableStateOf(settings())
        var attempts = 0
        setContent { NaviampAccountSwitcher(state.value, actions(switch = {
            attempts++
            state.value = state.value.copy(accountSwitcher = state.value.accountSwitcher.copy(connecting = true))
        }), NaviampColors.Dark) }
        onNodeWithTag(naviampAccountCardTag("alice")).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(naviampAccountCardTag("bob")).performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag(naviampAccountCardTag("bob")).assertIsNotEnabled()
        onNodeWithTag(NaviampAccountAddTag).assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(accountSwitcher = NaviampAccountSwitcherUi(
            visible = true, error = NaviampAccountSwitcherError.ConnectionFailed)) }
        // Android's dialog has its own frame clock; wait for the scheduled focus restoration.
        waitUntil(timeoutMillis = 5_000) {
            onNodeWithTag(naviampAccountCardTag("bob")).fetchSemanticsNode().config
                .getOrElse(SemanticsProperties.Focused) { false }
        }
        onNodeWithTag(naviampAccountCardTag("bob")).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag(naviampAccountCardTag("alice")).assertIsSelected()
        runOnIdle { assertEquals(2, attempts) }
    }

    @Test
    fun emptyChooserFocusesAddAccount() = runComposeUiTest {
        setContent { NaviampAccountSwitcher(settings(emptyList()), actions(), NaviampColors.Dark) }
        onNodeWithTag(NaviampAccountAddTag).assertIsFocused()
    }

    @Test
    fun backClosesTheChooserWithoutSelectingAnAccount() = runComposeUiTest {
        val state = mutableStateOf(settings())
        var selections = 0
        setContent { NaviampAccountSwitcher(state.value, actions(switch = { selections++ }, close = {
            state.value = state.value.copy(accountSwitcher = NaviampAccountSwitcherUi())
        }), NaviampColors.Dark) }
        onNodeWithTag(naviampAccountCardTag("alice")).performKeyInput { pressKey(Key.Back) }
        onNodeWithTag(NaviampAccountChooserTag).assertDoesNotExist()
        runOnIdle { assertEquals(0, selections) }
    }
}
