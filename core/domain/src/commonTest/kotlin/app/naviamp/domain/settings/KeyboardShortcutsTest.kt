package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardShortcutsTest {
    @Test
    fun platformDefaultsUseRareThreeModifierChords() {
        val windows = GlobalKeyboardShortcutSettings().resolvedBindings(DesktopShortcutPlatform.Windows)
        val mac = GlobalKeyboardShortcutSettings().resolvedBindings(DesktopShortcutPlatform.MacOS)

        assertEquals("Ctrl+Alt+Shift+Space", windows.getValue(GlobalShortcutAction.PlayPause)?.label(DesktopShortcutPlatform.Windows))
        assertEquals("Control+Option+Command+Space", mac.getValue(GlobalShortcutAction.PlayPause)?.label(DesktopShortcutPlatform.MacOS))
        assertTrue(windows.values.filterNotNull().all(KeyboardShortcutBinding::hasModifier))
    }

    @Test
    fun overridesArePlatformSpecificAndMayDisableOneAction() {
        val custom = KeyboardShortcutBinding(KeyboardShortcutKey.P, control = true, shift = true)
        val settings = GlobalKeyboardShortcutSettings()
            .withBinding(DesktopShortcutPlatform.Windows, GlobalShortcutAction.PlayPause, custom)
            .withBinding(DesktopShortcutPlatform.Windows, GlobalShortcutAction.NextTrack, null)

        assertEquals(custom, settings.resolvedBindings(DesktopShortcutPlatform.Windows)[GlobalShortcutAction.PlayPause])
        assertNull(settings.resolvedBindings(DesktopShortcutPlatform.Windows)[GlobalShortcutAction.NextTrack])
        assertEquals(
            defaultGlobalShortcutBinding(DesktopShortcutPlatform.MacOS, GlobalShortcutAction.PlayPause),
            settings.resolvedBindings(DesktopShortcutPlatform.MacOS)[GlobalShortcutAction.PlayPause],
        )
    }

    @Test
    fun assigningDuplicateBindingClearsItsPriorAction() {
        val binding = KeyboardShortcutBinding(KeyboardShortcutKey.P, control = true, alt = true)
        val settings = GlobalKeyboardShortcutSettings()
            .withBinding(DesktopShortcutPlatform.Windows, GlobalShortcutAction.PlayPause, binding)
            .withBinding(DesktopShortcutPlatform.Windows, GlobalShortcutAction.NextTrack, binding)

        assertNull(settings.resolvedBindings(DesktopShortcutPlatform.Windows)[GlobalShortcutAction.PlayPause])
        assertEquals(binding, settings.resolvedBindings(DesktopShortcutPlatform.Windows)[GlobalShortcutAction.NextTrack])
    }
}
