package app.naviamp.android

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.After
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Opt-in black-box check of the separately installed, R8-shrunk benchmark APK.
 * Android's package, Activity and accessibility APIs are the native boundaries under test.
 * Never targets the owner's normal installation or accesses its stored credentials.
 */
class AndroidOptimizedPackageSmokeInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val packageName = "app.naviamp.android.benchmark"

    @After fun retainTestOwnedWindow() {
        if (InstrumentationRegistry.getArguments().getString("optimizedPackageSmoke") != "true") return
        val root = automation.rootInActiveWindow ?: return
        if (root.packageName?.toString() != packageName) return
        val bitmap = automation.takeScreenshot() ?: return
        try {
            File(instrumentation.targetContext.cacheDir, "optimized-window.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun optimizedPackageConnectsBrowsesPlaysSeeksAndShowsAbout() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("optimizedPackageSmoke") == "true")
        // Package visibility intentionally hides unrelated apps from the debug target. Query only
        // the explicitly selected test APK through the runner's shell, without expanding app permissions.
        val installed = shell("dumpsys package $packageName")
        val flags = requireNotNull(Regex("pkgFlags=\\[([^]]*)]").find(installed)).groupValues[1]
        assertFalse(flags.contains("DEBUGGABLE"))
        val version = requireNotNull(Regex("versionCode=(\\d+)").find(installed)).groupValues[1].toInt()
        assertTrue(version == BuildConfig.VERSION_CODE)
        shell("am force-stop $packageName")
        launch()
        await("optimized app accessibility window") { nodes().isNotEmpty() }
        if (has("Collapse player")) click("Collapse player")
        if (has("Connection Details")) {
            fill("Server URL", "http://127.0.0.1:18080")
            fill("Username", "fixture")
            fill("Password", "fixture")
            click("Connect")
        }
        await("fixture connection") { !has("Connection Details") && has("Library") }
        record("non-debuggable optimized app connected")
        click("Library")
        click("Songs")
        await("fixture song") { has("TV lifecycle fixture", exact = false) }
        click("TV lifecycle fixture", exact = false)
        await("playing") { has("Pause") }
        await("playback slider") { progress() >= 0f }
        val before = progress()
        await("moving playback progress") { progress() > before + 0.001f }
        click("Pause")
        await("paused") { has("Play") && !has("Pause") }
        val slider = awaitNode("playback slider") { it.rangeInfo != null }
        val range = requireNotNull(slider.rangeInfo)
        val midpoint = (range.max + range.min) / 2
        assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, midpoint) }))
        await("paused midpoint seek") { progress() >= 0.49f }
        click("Play")
        await("resumed after seek") { has("Pause") }
        val sought = progress()
        await("moving progress after seek") { progress() > sought + 0.001f }
        click("Pause")
        record("original audio advanced, paused, sought to midpoint and resumed")
        if (has("Collapse player")) click("Collapse player")
        else if (!has("Settings")) automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        click("Settings")
        click("About", exact = false)
        await("candidate version") { has("v2.10.0", exact = false) }
        click("Changelog", exact = false)
        seekNode("Aurral changelog") { matches(it, "Aurral", exact = false) }
        record("About version and Aurral changelog rendered")
        // Recreate the actual optimized process, retaining only this test-owned profile.
        shell("am force-stop $packageName")
        launch()
        await("restored connection after cold launch") { has("RECENTLY ADDED MUSIC") && !has("Connection Details") }
        record("cold launch retained the isolated fixture connection")
    }

    private fun launch() = instrumentation.targetContext.startActivity(Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(packageName, "app.naviamp.android.MainActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun nodes(): List<AccessibilityNodeInfo> {
        // Refresh the native client's cache after Compose semantics/state transitions.
        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val root = automation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != packageName) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        visit(root)
        return result
    }

    private fun labels(node: AccessibilityNodeInfo) = listOfNotNull(
        node.text?.toString(), node.contentDescription?.toString(), node.hintText?.toString())

    private fun matches(node: AccessibilityNodeInfo, label: String, exact: Boolean) =
        labels(node).any { if (exact) it == label else it.contains(label) }

    private fun has(label: String, exact: Boolean = true) = nodes().any { matches(it, label, exact) }

    private fun click(label: String, exact: Boolean = true) {
        fun target(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
            generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
        // Page headings can have the same text as the navigation button.
        var node = seekNode(label) { matches(it, label, exact) && target(it) != null }
        if (target(node)?.let { it.isEnabled && it.isVisibleToUser } != true) {
            node = awaitNode("enabled $label") { candidate ->
                matches(candidate, label, exact) && target(candidate)?.let { it.isEnabled && it.isVisibleToUser } == true
            }
        }
        val clickable = requireNotNull(target(node)) { "Clickable $label" }
        assertTrue(clickable.isVisibleToUser && automation.rootInActiveWindow?.packageName?.toString() == packageName)
        val bounds = Rect().also(clickable::getBoundsInScreen)
        assertFalse(bounds.isEmpty)
        record("touch $label enabled=${clickable.isEnabled} bounds=$bounds")
        assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Activate $label")
        runCatching { automation.waitForIdle(200, 2_000) }
    }

    private fun fill(label: String, value: String) {
        fun containsLabel(node: AccessibilityNodeInfo): Boolean = matches(node, label, exact = false) ||
            (0 until node.childCount).any { node.getChild(it)?.let(::containsLabel) == true }
        val field = seekNode("editable $label") {
            it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SET_TEXT } && containsLabel(it)
        }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }), "Set $label")
    }

    private fun progress(): Float {
        val range = nodes().firstOrNull { it.rangeInfo != null }?.rangeInfo ?: return -1f
        return (range.current - range.min) / (range.max - range.min)
    }

    private fun seekNode(description: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(8) {
            nodes().firstOrNull(predicate)?.let { return it }
            val scroll = nodes().firstOrNull { node -> node.isScrollable && node.isVisibleToUser }
            if (scroll?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true)
                return awaitNode(description, predicate)
            SystemClock.sleep(200)
        }
        return awaitNode(description, predicate)
    }

    private fun awaitNode(description: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await(description) { nodes().firstOrNull(predicate).also { found = it } != null }
        return requireNotNull(found)
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(150)
        }
        error("Timed out: $description; optimized app labels=${nodes().flatMap(::labels).distinct()}")
    }

    private fun record(message: String) = instrumentation.sendStatus(2, Bundle().apply {
        putString("stream", "\nOptimized package: $message\n")
    })
}
