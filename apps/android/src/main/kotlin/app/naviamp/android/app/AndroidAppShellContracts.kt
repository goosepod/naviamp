package app.naviamp.android

import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampShellCapabilitiesUi

data class AndroidAppShellUiState(
    val modifier: Modifier,
    val presentation: NaviampAppShellUiState,
    val capabilities: NaviampShellCapabilitiesUi,
    val visualizerBandsProvider: () -> List<Float>,
)
