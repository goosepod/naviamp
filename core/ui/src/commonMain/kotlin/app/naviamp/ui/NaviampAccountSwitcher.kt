package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource

internal const val NaviampAccountChooserTag = "account-chooser"
internal const val NaviampAccountAddTag = "account-add"
internal fun naviampAccountCardTag(id: String) = "account-card-$id"

/** All account selection and failure policy is supplied by Core; this is shared remote-friendly UI. */
@Composable
internal fun NaviampAccountSwitcher(settings: NaviampConnectionSettingsUi,
    actions: NaviampConnectionSettingsActions, colors: NaviampColors) {
    val picker = settings.accountSwitcher
    if (!picker.visible) return
    val accounts = settings.connection.savedConnections
    val focusRequesters = remember(accounts.map { it.id }) {
        accounts.associate { it.id to FocusRequester() }
    }
    val addFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    var focusedId by remember { mutableStateOf(accounts.firstOrNull { it.current }?.id ?: accounts.firstOrNull()?.id) }
    var attemptedId by remember { mutableStateOf<String?>(null) }
    NaviampPopupPresence()
    Dialog(onDismissRequest = actions.onCloseAccounts,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = LocalFocusManager.current
        LaunchedEffect(picker.connecting, accounts.map { it.id }) {
            if (picker.connecting) {
                withFrameNanos { }
                cancelFocus.requestFocus()
            } else {
                val index = accounts.indexOfFirst { it.id == (attemptedId ?: focusedId) }
                if (index >= 0) {
                    list.scrollToItem(index)
                    withFrameNanos { }
                    focusRequesters.getValue(accounts[index].id).requestFocus()
                } else {
                    list.scrollToItem(accounts.size)
                    withFrameNanos { }
                    addFocus.requestFocus()
                }
                attemptedId = null
            }
        }
        Column(Modifier.widthIn(max = 1080.dp).fillMaxWidth()
            .background(colors.controlSurface, RoundedCornerShape(24.dp))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> focus.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> focus.moveFocus(FocusDirection.Right)
                    Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                    Key.Back, Key.Escape -> { actions.onCloseAccounts(); true }
                    else -> false
                }
            }
            .verticalScroll(rememberScrollState()).padding(36.dp).testTag(NaviampAccountChooserTag),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(Res.string.tv_accounts_title), color = colors.primaryText,
                fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.tv_accounts_description), color = colors.secondaryText, fontSize = 18.sp)
            LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(accounts, key = { _, account -> account.id }) { _, account ->
                    AccountCard(account, enabled = !picker.connecting, colors = colors,
                        modifier = Modifier.focusRequester(focusRequesters.getValue(account.id))
                            .onFocusChanged { if (it.isFocused && !picker.connecting) focusedId = account.id },
                        onClick = { attemptedId = account.id; actions.onSwitchAccount(account) })
                }
                item(key = NaviampAccountAddTag) {
                    val label = stringResource(Res.string.tv_account_add)
                    var focused by remember { mutableStateOf(false) }
                    Button(onClick = actions.onAddAccount, enabled = !picker.connecting,
                        shape = RoundedCornerShape(18.dp),
                        colors = accountCardColors(focused, colors),
                        contentPadding = PaddingValues(18.dp),
                        modifier = Modifier.width(190.dp).height(230.dp).focusRequester(addFocus)
                            .onFocusChanged { focused = it.isFocused; if (it.isFocused) focusedId = null }
                            .testTag(NaviampAccountAddTag)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Box(Modifier.size(88.dp).background((if (focused) colors.background else colors.primaryText).copy(alpha = .12f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(NaviampIcons.Plus, contentDescription = null, modifier = Modifier.size(38.dp))
                            }
                            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (picker.connecting) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.primaryText, strokeWidth = 2.dp)
                Text(stringResource(Res.string.tv_account_switching), color = colors.secondaryText, fontSize = 17.sp)
            }
            if (picker.error != null) Text(stringResource(Res.string.tv_account_switch_failed),
                color = colors.primaryText, fontSize = 17.sp)
            TextButton(onClick = actions.onCloseAccounts, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(stringResource(Res.string.common_cancel), color = colors.primaryText)
            }
        }
    }
}

@Composable
private fun accountCardColors(focused: Boolean, colors: NaviampColors) = ButtonDefaults.buttonColors(
    containerColor = if (focused) colors.primaryText else colors.background,
    contentColor = if (focused) colors.background else colors.primaryText,
)

@Composable
private fun AccountCard(account: NaviampSavedConnectionUi, enabled: Boolean, colors: NaviampColors,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val name = account.username.ifBlank { account.displayName }
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(18.dp),
        colors = accountCardColors(focused, colors), contentPadding = PaddingValues(18.dp),
        modifier = modifier.width(190.dp).height(230.dp).onFocusChanged { focused = it.isFocused }
            .semantics { selected = account.current }.testTag(naviampAccountCardTag(account.id))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(88.dp).background((if (focused) colors.background else colors.primaryText).copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(account.displayName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (account.current) Text(stringResource(Res.string.common_current), fontSize = 14.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun NaviampAccountNavigationButton(account: NaviampSavedConnectionUi?, colors: NaviampColors,
    focusRequester: FocusRequester, canFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val name = account?.username?.ifBlank { account.displayName } ?: stringResource(Res.string.tv_accounts)
    val description = stringResource(Res.string.tv_account_nav_label, name)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).focusRequester(focusRequester)
        .focusProperties { this.canFocus = canFocus }
        .onFocusChanged { focused = it.isFocused }
        .background(if (focused) colors.primaryText else colors.controlSurface, CircleShape)
        .semantics { contentDescription = description }) {
        Text(name.take(1).uppercase(), color = if (focused) colors.background else colors.primaryText,
            fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}
