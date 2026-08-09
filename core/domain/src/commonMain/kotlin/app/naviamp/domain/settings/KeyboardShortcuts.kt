package app.naviamp.domain.settings

import kotlinx.serialization.Serializable

@Serializable
enum class DesktopShortcutPlatform(val label: String) {
    Windows("Windows"),
    MacOS("macOS"),
    Linux("Linux"),
}

@Serializable
enum class GlobalShortcutAction(val label: String) {
    PlayPause("Play/Pause"),
    NextTrack("Next track"),
    Previous("Previous"),
    VolumeUp("Volume up"),
    VolumeDown("Volume down"),
    BringToFront("Bring Naviamp to front"),
}

@Serializable
enum class KeyboardShortcutKey(val label: String) {
    Space("Space"),
    Left("Left"),
    Right("Right"),
    Up("Up"),
    Down("Down"),
    Home("Home"),
    End("End"),
    PageUp("Page Up"),
    PageDown("Page Down"),
    A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"), H("H"), I("I"),
    J("J"), K("K"), L("L"), M("M"), N("N"), O("O"), P("P"), Q("Q"), R("R"),
    S("S"), T("T"), U("U"), V("V"), W("W"), X("X"), Y("Y"), Z("Z"),
    Digit0("0"), Digit1("1"), Digit2("2"), Digit3("3"), Digit4("4"),
    Digit5("5"), Digit6("6"), Digit7("7"), Digit8("8"), Digit9("9"),
    Minus("-"), Equals("="),
    F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"),
    F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12"),
}

@Serializable
data class KeyboardShortcutBinding(
    val key: KeyboardShortcutKey,
    val control: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
) {
    val hasModifier: Boolean get() = control || alt || shift || meta

    fun label(platform: DesktopShortcutPlatform): String = buildList {
        if (control) add(if (platform == DesktopShortcutPlatform.MacOS) "Control" else "Ctrl")
        if (alt) add(if (platform == DesktopShortcutPlatform.MacOS) "Option" else "Alt")
        if (shift) add("Shift")
        if (meta) add(if (platform == DesktopShortcutPlatform.MacOS) "Command" else "Meta")
        add(key.label)
    }.joinToString("+")
}

@Serializable
data class GlobalKeyboardShortcutSettings(
    val enabled: Boolean = false,
    val bindingsByPlatform: Map<DesktopShortcutPlatform, Map<GlobalShortcutAction, KeyboardShortcutBinding?>> =
        emptyMap(),
) {
    fun normalized(): GlobalKeyboardShortcutSettings = copy(
        bindingsByPlatform = bindingsByPlatform.mapValues { (_, bindings) ->
            bindings.mapValues { (_, binding) -> binding?.takeIf(KeyboardShortcutBinding::hasModifier) }
        },
    )
}

fun GlobalKeyboardShortcutSettings.resolvedBindings(
    platform: DesktopShortcutPlatform,
): Map<GlobalShortcutAction, KeyboardShortcutBinding?> {
    val overrides = bindingsByPlatform[platform].orEmpty()
    return GlobalShortcutAction.entries.associateWith { action ->
        if (overrides.containsKey(action)) overrides[action] else defaultGlobalShortcutBinding(platform, action)
    }
}

fun GlobalKeyboardShortcutSettings.withBinding(
    platform: DesktopShortcutPlatform,
    action: GlobalShortcutAction,
    binding: KeyboardShortcutBinding?,
): GlobalKeyboardShortcutSettings {
    val platformBindings = bindingsByPlatform[platform].orEmpty().toMutableMap()
    if (binding != null) {
        platformBindings.entries
            .filter { (otherAction, otherBinding) -> otherAction != action && otherBinding == binding }
            .forEach { (otherAction, _) -> platformBindings[otherAction] = null }
    }
    platformBindings[action] = binding
    return copy(bindingsByPlatform = bindingsByPlatform + (platform to platformBindings)).normalized()
}

fun GlobalKeyboardShortcutSettings.resetBindings(
    platform: DesktopShortcutPlatform,
): GlobalKeyboardShortcutSettings = copy(bindingsByPlatform = bindingsByPlatform - platform)

fun defaultGlobalShortcutBinding(
    platform: DesktopShortcutPlatform,
    action: GlobalShortcutAction,
): KeyboardShortcutBinding {
    val key = when (action) {
        GlobalShortcutAction.PlayPause -> KeyboardShortcutKey.Space
        GlobalShortcutAction.NextTrack -> KeyboardShortcutKey.Right
        GlobalShortcutAction.Previous -> KeyboardShortcutKey.Left
        GlobalShortcutAction.VolumeUp -> KeyboardShortcutKey.Up
        GlobalShortcutAction.VolumeDown -> KeyboardShortcutKey.Down
        GlobalShortcutAction.BringToFront -> KeyboardShortcutKey.N
    }
    return if (platform == DesktopShortcutPlatform.MacOS) {
        KeyboardShortcutBinding(key, control = true, alt = true, meta = true)
    } else {
        KeyboardShortcutBinding(key, control = true, alt = true, shift = true)
    }
}

const val GlobalShortcutVolumeStepPercent = 5
