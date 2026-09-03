package app.naviamp.ui

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampTextInputKindTest {
    @Test
    fun technicalInputDisablesCorrectionAndCapitalization() {
        val options = NaviampTextInputKind.Technical.keyboardOptions(ImeAction.Default)

        assertEquals(KeyboardCapitalization.None, options.capitalization)
        assertEquals(false, options.autoCorrectEnabled)
        assertEquals(KeyboardType.Ascii, options.keyboardType)
    }

    @Test
    fun urlInputUsesUriKeyboardWithoutCorrection() {
        val options = NaviampTextInputKind.Url.keyboardOptions(ImeAction.Search)

        assertEquals(KeyboardCapitalization.None, options.capitalization)
        assertEquals(false, options.autoCorrectEnabled)
        assertEquals(KeyboardType.Uri, options.keyboardType)
        assertEquals(ImeAction.Search, options.imeAction)
    }

    @Test
    fun passwordInputUsesPasswordKeyboardWithoutCorrection() {
        val options = NaviampTextInputKind.Password.keyboardOptions(ImeAction.Default)

        assertEquals(KeyboardCapitalization.None, options.capitalization)
        assertEquals(false, options.autoCorrectEnabled)
        assertEquals(KeyboardType.Password, options.keyboardType)
    }
}
