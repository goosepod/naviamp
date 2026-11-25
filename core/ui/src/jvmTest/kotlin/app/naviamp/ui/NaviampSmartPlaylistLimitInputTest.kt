package app.naviamp.ui

import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampSmartPlaylistLimitInputTest {
    @Test
    fun trackCountAndPercentLimitsOpenTheNumberKeyboard() {
        assertEquals(KeyboardType.Number, SmartPlaylistLimitKeyboardOptions.keyboardType)
    }
}
