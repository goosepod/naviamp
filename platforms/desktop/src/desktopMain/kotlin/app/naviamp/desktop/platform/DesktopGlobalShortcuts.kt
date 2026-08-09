package app.naviamp.desktop.platform

import app.naviamp.domain.settings.DesktopShortcutPlatform
import app.naviamp.domain.settings.GlobalShortcutAction
import app.naviamp.domain.settings.KeyboardShortcutBinding
import app.naviamp.domain.settings.KeyboardShortcutKey
import app.naviamp.ui.GlobalShortcutRegistrationState
import app.naviamp.ui.GlobalShortcutRegistrationUi
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.Carbon
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface DesktopGlobalShortcutRegistrar : AutoCloseable {
    fun register(
        bindings: Map<GlobalShortcutAction, KeyboardShortcutBinding?>,
        onShortcut: (GlobalShortcutAction) -> Unit,
    ): Map<GlobalShortcutAction, GlobalShortcutRegistrationUi>
}

fun desktopGlobalShortcutRegistrar(
    platform: DesktopShortcutPlatform,
): DesktopGlobalShortcutRegistrar = when (platform) {
    DesktopShortcutPlatform.Windows -> WindowsGlobalShortcutRegistrar()
    DesktopShortcutPlatform.MacOS -> MacGlobalShortcutRegistrar()
    DesktopShortcutPlatform.Linux -> LinuxX11GlobalShortcutRegistrar()
}

private class WindowsGlobalShortcutRegistrar : DesktopGlobalShortcutRegistrar {
    private var worker: Thread? = null
    @Volatile private var workerThreadId: Int = 0

    override fun register(
        bindings: Map<GlobalShortcutAction, KeyboardShortcutBinding?>,
        onShortcut: (GlobalShortcutAction) -> Unit,
    ): Map<GlobalShortcutAction, GlobalShortcutRegistrationUi> {
        close()
        val statuses = mutableMapOf<GlobalShortcutAction, GlobalShortcutRegistrationUi>()
        val ready = CountDownLatch(1)
        worker = Thread({
            val user32 = User32.INSTANCE
            workerThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val registered = mutableSetOf<Int>()
            bindings.forEach { (action, binding) ->
                if (binding == null) return@forEach
                val id = action.ordinal + 1
                val success = user32.RegisterHotKey(
                    null,
                    id,
                    binding.windowsModifiers() or ModNoRepeat,
                    binding.key.awtVirtualKey(),
                )
                if (success) registered += id
                statuses[action] = if (success) registeredStatus() else conflictStatus()
            }
            ready.countDown()
            val message = WinUser.MSG()
            while (user32.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == WinUser.WM_HOTKEY) {
                    GlobalShortcutAction.entries.getOrNull(message.wParam.toInt() - 1)?.let(onShortcut)
                }
            }
            registered.forEach { id -> user32.UnregisterHotKey(Pointer.NULL, id) }
            workerThreadId = 0
        }, "Naviamp-Windows-Global-Shortcuts").apply {
            isDaemon = true
            start()
        }
        ready.await(3, TimeUnit.SECONDS)
        return statuses.toMap()
    }

    override fun close() {
        val thread = worker ?: return
        val threadId = workerThreadId
        if (threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, WinDef.WPARAM(), WinDef.LPARAM())
        }
        thread.join(1_000)
        worker = null
    }
}

private class MacGlobalShortcutRegistrar : DesktopGlobalShortcutRegistrar {
    private val carbon = Carbon.INSTANCE
    private val hotKeys = mutableListOf<Pointer>()
    private var eventHandler: Pointer? = null
    private var callback: Carbon.EventHandlerProcPtr? = null

    override fun register(
        bindings: Map<GlobalShortcutAction, KeyboardShortcutBinding?>,
        onShortcut: (GlobalShortcutAction) -> Unit,
    ): Map<GlobalShortcutAction, GlobalShortcutRegistrationUi> {
        close()
        val actionById = GlobalShortcutAction.entries.associateBy { it.ordinal + 1 }
        val handlerCallback = Carbon.EventHandlerProcPtr { _, event, _ ->
            val id = Carbon.EventHotKeyID()
            carbon.GetEventParameter(
                event,
                fourCharCode("----"),
                fourCharCode("hkid"),
                null,
                id.size(),
                null,
                id,
            )
            id.read()
            actionById[id.id]?.let(onShortcut)
            0
        }
        callback = handlerCallback
        val type = Carbon.EventTypeSpec().apply {
            eventClass = fourCharCode("keyb")
            eventKind = 5
            write()
        }
        val handlerReference = PointerByReference()
        val installResult = carbon.InstallEventHandler(
            carbon.GetEventDispatcherTarget(),
            handlerCallback,
            1,
            arrayOf(type),
            null,
            handlerReference,
        )
        if (installResult != 0) {
            return bindings.filterValues { it != null }.keys.associateWith {
                unavailableStatus("macOS could not install the global shortcut handler")
            }
        }
        eventHandler = handlerReference.value
        return bindings.mapNotNull { (action, binding) ->
            binding ?: return@mapNotNull null
            val id = Carbon.EventHotKeyID.ByValue().apply {
                signature = fourCharCode("Navi")
                this.id = action.ordinal + 1
                write()
            }
            val hotKeyReference = PointerByReference()
            val result = carbon.RegisterEventHotKey(
                binding.key.macVirtualKey(),
                binding.macModifiers(),
                id,
                carbon.GetEventDispatcherTarget(),
                0,
                hotKeyReference,
            )
            if (result == 0) hotKeys += hotKeyReference.value
            action to if (result == 0) registeredStatus() else conflictStatus()
        }.toMap()
    }

    override fun close() {
        hotKeys.forEach(carbon::UnregisterEventHotKey)
        hotKeys.clear()
        eventHandler?.let(carbon::RemoveEventHandler)
        eventHandler = null
        callback = null
    }
}

private class LinuxX11GlobalShortcutRegistrar : DesktopGlobalShortcutRegistrar {
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun register(
        bindings: Map<GlobalShortcutAction, KeyboardShortcutBinding?>,
        onShortcut: (GlobalShortcutAction) -> Unit,
    ): Map<GlobalShortcutAction, GlobalShortcutRegistrationUi> {
        close()
        if (System.getenv("WAYLAND_DISPLAY").orEmpty().isNotBlank() && System.getenv("DISPLAY").isNullOrBlank()) {
            return bindings.filterValues { it != null }.keys.associateWith {
                unavailableStatus("This Wayland session does not expose an X11 global-shortcut bridge")
            }
        }
        val statuses = mutableMapOf<GlobalShortcutAction, GlobalShortcutRegistrationUi>()
        val ready = CountDownLatch(1)
        running.set(true)
        worker = Thread({
            val x11 = X11.INSTANCE
            val display = x11.XOpenDisplay(null)
            if (display == null) {
                bindings.filterValues { it != null }.keys.forEach { action ->
                    statuses[action] = unavailableStatus("Could not connect to the X11 desktop session")
                }
                ready.countDown()
                return@Thread
            }
            val root = x11.XDefaultRootWindow(display)
            val actionByKey = mutableMapOf<Pair<Int, Int>, GlobalShortcutAction>()
            bindings.forEach { (action, binding) ->
                if (binding == null) return@forEach
                val keyCode = x11.XKeysymToKeycode(display, X11.KeySym(binding.key.x11KeySym())).toInt() and 0xff
                val modifiers = binding.x11Modifiers()
                if (keyCode == 0) {
                    statuses[action] = unavailableStatus("The selected key is unavailable on this keyboard layout")
                } else {
                    listOf(0, X11.LockMask, X11.Mod2Mask, X11.LockMask or X11.Mod2Mask).forEach { locks ->
                        x11.XGrabKey(display, keyCode, modifiers or locks, root, 1, X11.GrabModeAsync, X11.GrabModeAsync)
                    }
                    actionByKey[keyCode to modifiers] = action
                    statuses[action] = registeredStatus()
                }
            }
            x11.XSync(display, false)
            ready.countDown()
            while (running.get()) {
                while (x11.XPending(display) > 0) {
                    val event = X11.XEvent()
                    x11.XNextEvent(display, event)
                    if (event.type == X11.KeyPress) {
                        event.setType(X11.XKeyEvent::class.java)
                        event.read()
                        val modifiers = event.xkey.state and (X11.ShiftMask or X11.ControlMask or X11.Mod1Mask or X11.Mod4Mask)
                        actionByKey[event.xkey.keycode to modifiers]?.let(onShortcut)
                    }
                }
                Thread.sleep(20)
            }
            bindings.values.filterNotNull().forEach { binding ->
                val keyCode = x11.XKeysymToKeycode(display, X11.KeySym(binding.key.x11KeySym())).toInt() and 0xff
                listOf(0, X11.LockMask, X11.Mod2Mask, X11.LockMask or X11.Mod2Mask).forEach { locks ->
                    x11.XUngrabKey(display, keyCode, binding.x11Modifiers() or locks, root)
                }
            }
            x11.XCloseDisplay(display)
        }, "Naviamp-Linux-Global-Shortcuts").apply {
            isDaemon = true
            start()
        }
        ready.await(3, TimeUnit.SECONDS)
        return statuses.toMap()
    }

    override fun close() {
        running.set(false)
        worker?.join(1_000)
        worker = null
    }
}

private fun registeredStatus() = GlobalShortcutRegistrationUi(GlobalShortcutRegistrationState.Registered)
private fun conflictStatus() = GlobalShortcutRegistrationUi(
    GlobalShortcutRegistrationState.Conflict,
    "Unavailable because another app or the operating system uses it",
)
private fun unavailableStatus(detail: String) =
    GlobalShortcutRegistrationUi(GlobalShortcutRegistrationState.Unavailable, detail)

private fun KeyboardShortcutBinding.windowsModifiers(): Int =
    (if (alt) WinUser.MOD_ALT else 0) or
        (if (control) WinUser.MOD_CONTROL else 0) or
        (if (shift) WinUser.MOD_SHIFT else 0) or
        (if (meta) WinUser.MOD_WIN else 0)

private fun KeyboardShortcutBinding.macModifiers(): Int =
    (if (control) Carbon.controlKey else 0) or
        (if (alt) Carbon.optionKey else 0) or
        (if (shift) Carbon.shiftKey else 0) or
        (if (meta) Carbon.cmdKey else 0)

private fun KeyboardShortcutBinding.x11Modifiers(): Int =
    (if (control) X11.ControlMask else 0) or
        (if (alt) X11.Mod1Mask else 0) or
        (if (shift) X11.ShiftMask else 0) or
        (if (meta) X11.Mod4Mask else 0)

private fun KeyboardShortcutKey.awtVirtualKey(): Int = when (this) {
    KeyboardShortcutKey.Space -> KeyEvent.VK_SPACE
    KeyboardShortcutKey.Left -> KeyEvent.VK_LEFT
    KeyboardShortcutKey.Right -> KeyEvent.VK_RIGHT
    KeyboardShortcutKey.Up -> KeyEvent.VK_UP
    KeyboardShortcutKey.Down -> KeyEvent.VK_DOWN
    KeyboardShortcutKey.Home -> KeyEvent.VK_HOME
    KeyboardShortcutKey.End -> KeyEvent.VK_END
    KeyboardShortcutKey.PageUp -> KeyEvent.VK_PAGE_UP
    KeyboardShortcutKey.PageDown -> KeyEvent.VK_PAGE_DOWN
    KeyboardShortcutKey.Minus -> KeyEvent.VK_MINUS
    KeyboardShortcutKey.Equals -> KeyEvent.VK_EQUALS
    else -> label.singleOrNull()?.code ?: when (this) {
        KeyboardShortcutKey.F1 -> KeyEvent.VK_F1
        KeyboardShortcutKey.F2 -> KeyEvent.VK_F2
        KeyboardShortcutKey.F3 -> KeyEvent.VK_F3
        KeyboardShortcutKey.F4 -> KeyEvent.VK_F4
        KeyboardShortcutKey.F5 -> KeyEvent.VK_F5
        KeyboardShortcutKey.F6 -> KeyEvent.VK_F6
        KeyboardShortcutKey.F7 -> KeyEvent.VK_F7
        KeyboardShortcutKey.F8 -> KeyEvent.VK_F8
        KeyboardShortcutKey.F9 -> KeyEvent.VK_F9
        KeyboardShortcutKey.F10 -> KeyEvent.VK_F10
        KeyboardShortcutKey.F11 -> KeyEvent.VK_F11
        KeyboardShortcutKey.F12 -> KeyEvent.VK_F12
        else -> error("Unsupported key: $this")
    }
}

private fun KeyboardShortcutKey.macVirtualKey(): Int = when (this) {
    KeyboardShortcutKey.A -> 0x00; KeyboardShortcutKey.S -> 0x01; KeyboardShortcutKey.D -> 0x02
    KeyboardShortcutKey.F -> 0x03; KeyboardShortcutKey.H -> 0x04; KeyboardShortcutKey.G -> 0x05
    KeyboardShortcutKey.Z -> 0x06; KeyboardShortcutKey.X -> 0x07; KeyboardShortcutKey.C -> 0x08
    KeyboardShortcutKey.V -> 0x09; KeyboardShortcutKey.B -> 0x0B; KeyboardShortcutKey.Q -> 0x0C
    KeyboardShortcutKey.W -> 0x0D; KeyboardShortcutKey.E -> 0x0E; KeyboardShortcutKey.R -> 0x0F
    KeyboardShortcutKey.Y -> 0x10; KeyboardShortcutKey.T -> 0x11; KeyboardShortcutKey.Digit1 -> 0x12
    KeyboardShortcutKey.Digit2 -> 0x13; KeyboardShortcutKey.Digit3 -> 0x14; KeyboardShortcutKey.Digit4 -> 0x15
    KeyboardShortcutKey.Digit6 -> 0x16; KeyboardShortcutKey.Digit5 -> 0x17; KeyboardShortcutKey.Equals -> 0x18
    KeyboardShortcutKey.Digit9 -> 0x19; KeyboardShortcutKey.Digit7 -> 0x1A; KeyboardShortcutKey.Minus -> 0x1B
    KeyboardShortcutKey.Digit8 -> 0x1C; KeyboardShortcutKey.Digit0 -> 0x1D; KeyboardShortcutKey.O -> 0x1F
    KeyboardShortcutKey.U -> 0x20; KeyboardShortcutKey.I -> 0x22; KeyboardShortcutKey.P -> 0x23
    KeyboardShortcutKey.L -> 0x25; KeyboardShortcutKey.J -> 0x26; KeyboardShortcutKey.K -> 0x28
    KeyboardShortcutKey.N -> 0x2D; KeyboardShortcutKey.M -> 0x2E; KeyboardShortcutKey.Space -> 0x31
    KeyboardShortcutKey.F1 -> 0x7A; KeyboardShortcutKey.F2 -> 0x78; KeyboardShortcutKey.F3 -> 0x63
    KeyboardShortcutKey.F4 -> 0x76; KeyboardShortcutKey.F5 -> 0x60; KeyboardShortcutKey.F6 -> 0x61
    KeyboardShortcutKey.F7 -> 0x62; KeyboardShortcutKey.F8 -> 0x64; KeyboardShortcutKey.F9 -> 0x65
    KeyboardShortcutKey.F10 -> 0x6D; KeyboardShortcutKey.F11 -> 0x67; KeyboardShortcutKey.F12 -> 0x6F
    KeyboardShortcutKey.Home -> 0x73; KeyboardShortcutKey.End -> 0x77
    KeyboardShortcutKey.PageUp -> 0x74; KeyboardShortcutKey.PageDown -> 0x79
    KeyboardShortcutKey.Left -> 0x7B; KeyboardShortcutKey.Right -> 0x7C
    KeyboardShortcutKey.Down -> 0x7D; KeyboardShortcutKey.Up -> 0x7E
}

private fun KeyboardShortcutKey.x11KeySym(): Long = when (this) {
    KeyboardShortcutKey.Space -> 0x20L
    KeyboardShortcutKey.Left -> 0xFF51L
    KeyboardShortcutKey.Up -> 0xFF52L
    KeyboardShortcutKey.Right -> 0xFF53L
    KeyboardShortcutKey.Down -> 0xFF54L
    KeyboardShortcutKey.PageUp -> 0xFF55L
    KeyboardShortcutKey.PageDown -> 0xFF56L
    KeyboardShortcutKey.End -> 0xFF57L
    KeyboardShortcutKey.Home -> 0xFF50L
    KeyboardShortcutKey.F1, KeyboardShortcutKey.F2, KeyboardShortcutKey.F3, KeyboardShortcutKey.F4,
    KeyboardShortcutKey.F5, KeyboardShortcutKey.F6, KeyboardShortcutKey.F7, KeyboardShortcutKey.F8,
    KeyboardShortcutKey.F9, KeyboardShortcutKey.F10, KeyboardShortcutKey.F11, KeyboardShortcutKey.F12,
    -> 0xFFBEL + (ordinal - KeyboardShortcutKey.F1.ordinal)
    else -> label.single().lowercaseChar().code.toLong()
}

private fun fourCharCode(value: String): Int {
    require(value.length == 4)
    return value.fold(0) { result, char -> (result shl 8) or char.code }
}

private const val ModNoRepeat = 0x4000
