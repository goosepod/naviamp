package app.naviamp.android

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.json.JSONArray

/** Explicitly opted-in black-box acceptance probe for the signed app and OS-owned network UI.
 * Uses Android accessibility, Activity, screenshot and audio-focus APIs; never reads private app data.
 * Scripts and captured windows belong in ignored local evidence because they can contain account labels.
 */
class AndroidReleaseAcceptanceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private var packageName = "app.naviamp.android"

    @Test fun runAuthorizedAcceptanceSteps() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("releaseAcceptance") == "true")
        packageName = args.getString("acceptancePackage") ?: packageName
        require(packageName in setOf("app.naviamp.android", "app.naviamp.android.v2test", "com.tailscale.ipn"))
        val rememberedSessionIds = mutableMapOf<String, String>()
        val rememberedSessionQueues = mutableMapOf<String, List<String>>()
        val steps = JSONArray(args.getString("acceptanceSteps") ?: "[]")
        for (index in 0 until steps.length()) {
            val step = steps.getJSONObject(index)
            when (step.getString("op")) {
                "launch" -> launch()
                "click" -> click(step.getString("label"), step.optBoolean("exact", true))
                "fill" -> fill(step.getString("label"), step.getString("value"))
                "clickWithin" -> {
                    val scope = seekNode(step.getString("scope")) { matches(it, step.getString("scope"), true) }
                    fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                        if (matches(node, step.getString("label"), true)) return node
                        return (0 until node.childCount).mapNotNull { node.getChild(it)?.let(::find) }.firstOrNull()
                    }
                    val label = generateSequence(scope) { it.parent }.mapNotNull(::find).first()
                    val target = generateSequence(label) { it.parent }.first { it.isClickable }
                    assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    runCatching { automation.waitForIdle(200, 2000) }
                }
                "scroll" -> repeat(step.optInt("count", 1).also { require(it in 1..10) }) {
                    nodes().firstOrNull { it.isScrollable && it.isVisibleToUser }?.performAction(
                        if (step.optString("direction") == "backward") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    SystemClock.sleep(150)
                }
                "toggle" -> {
                    val label = seekNode(step.getString("label")) { matches(it, step.getString("label"), true) }
                    val parent = requireNotNull(label.parent)
                    val checkbox = (0 until parent.childCount).mapNotNull(parent::getChild).single { it.isCheckable }
                    val wanted = step.getBoolean("checked")
                    if (checkbox.isChecked != wanted) assertTrue(checkbox.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    record("set adjacent checkbox ${step.getString("label")}=$wanted")
                }
                "expect" -> await(step.getString("label")) { has(step.getString("label"), step.optBoolean("exact", true)) }
                "expectFocus" -> await("focused ${step.getString("label")}") {
                    fun containsLabel(node: AccessibilityNodeInfo): Boolean =
                        matches(node, step.getString("label"), true) ||
                            (0 until node.childCount).any { node.getChild(it)?.let(::containsLabel) == true }
                    val current = nodes()
                    val matched = current.any { it.isFocused && containsLabel(it) }
                    if (step.optBoolean("negate")) current.any { it.isFocused } && !matched else matched
                }
                "session" -> {
                    val expected = when (step.optString("state")) {
                        "playing" -> android.media.session.PlaybackState.STATE_PLAYING
                        "paused" -> android.media.session.PlaybackState.STATE_PAUSED
                        "" -> null
                        else -> error("Unsupported native session state")
                    }
                    val minimum = step.optInt("minimumQueueCount", 0).also { require(it >= 0) }
                    val exact = if (step.has("queueCount")) step.getInt("queueCount") else null
                    val changedFrom = step.optString("changedFrom").takeIf(String::isNotEmpty)?.let {
                        requireNotNull(rememberedSessionIds[it]) { "No remembered native session $it" }
                    }
                    val retained = step.optString("retainsQueueFrom").takeIf(String::isNotEmpty)?.let {
                        requireNotNull(rememberedSessionQueues[it]) { "No remembered native queue $it" }
                    }
                    var observed: NativeSession? = null
                    await("native session state/queue") {
                        nativeSession().also { observed = it }?.let {
                            (expected == null || it.state == expected) && it.queueCount >= minimum &&
                                (exact == null || it.queueCount == exact) &&
                                (!step.has("title") || it.title == step.getString("title")) &&
                                (!step.has("queueIndex") || it.queueIndex == step.getLong("queueIndex")) &&
                                (!step.has("maximumPositionMs") || (it.positionMillis ?: Long.MAX_VALUE) <= step.getLong("maximumPositionMs")) &&
                                (!step.has("minimumPositionMs") || (it.positionMillis ?: -1) >= step.getLong("minimumPositionMs")) &&
                                (changedFrom == null || (!it.mediaId.isNullOrBlank() && it.mediaId != changedFrom)) &&
                                (retained == null || retained.groupingBy { id -> id }.eachCount().all { (id, count) ->
                                    it.queueItems.count { current -> current == id } >= count
                                })
                        } == true
                    }
                    val value = requireNotNull(observed)
                    step.optString("remember").takeIf(String::isNotEmpty)?.let {
                        rememberedSessionIds[it] = requireNotNull(value.mediaId)
                        rememberedSessionQueues[it] = value.queueItems
                    }
                    record("native session state=${value.state} queueCount=${value.queueCount} queueIndex=${value.queueIndex} positionMs=${value.positionMillis}")
                }
                "transport" -> {
                    automation.adoptShellPermissionIdentity(android.Manifest.permission.MEDIA_CONTENT_CONTROL)
                    try {
                        val controller = instrumentation.targetContext
                            .getSystemService(android.media.session.MediaSessionManager::class.java)
                            .getActiveSessions(null).single { it.packageName == packageName }
                        val controls = controller.transportControls
                        when (step.getString("action")) {
                            "play" -> controls.play()
                            "pause" -> controls.pause()
                            "next" -> controls.skipToNext()
                            "previous" -> controls.skipToPrevious()
                            "queue" -> controls.skipToQueueItem(step.getLong("index").also {
                                require(controller.queue.orEmpty().any { item -> item.queueId == it })
                            })
                            "seekNearEnd" -> {
                                val duration = requireNotNull(controller.metadata)
                                    .getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
                                val remaining = step.optLong("remainingMs", 10000).also { require(it in 5000..60000) }
                                require(duration > remaining)
                                controls.seekTo(duration - remaining)
                            }
                            else -> error("Unsupported native transport action")
                        }
                    } finally { automation.dropShellPermissionIdentity() }
                }
                "back" -> automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "key" -> {
                    assertTrue(automation.rootInActiveWindow?.packageName?.toString() == packageName,
                        "Remote input must belong to the selected app")
                    val key = when (step.getString("key")) {
                        "up" -> android.view.KeyEvent.KEYCODE_DPAD_UP
                        "down" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        "left" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        "right" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                        "center" -> android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        else -> error("Unsupported remote key")
                    }
                    instrumentation.sendKeyDownUpSync(key)
                    runCatching { automation.waitForIdle(200, 2000) }
                }
                "home" -> automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "lock" -> assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
                "focus" -> {
                    require(packageName != instrumentation.targetContext.packageName) {
                        "Audio focus competition requires a separate target UID"
                    }
                    // Android 15+ grants focus only to the foreground UID. Bring the isolated
                    // test host forward, request a real OS interruption, then return to the release.
                    instrumentation.targetContext.startActivity(Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(instrumentation.targetContext.packageName, "app.naviamp.android.MainActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    SystemClock.sleep(1000)
                    val manager = instrumentation.targetContext.getSystemService(android.media.AudioManager::class.java)
                    val request = android.media.AudioFocusRequest.Builder(if (step.optBoolean("permanent"))
                        android.media.AudioManager.AUDIOFOCUS_GAIN else android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                        .setOnAudioFocusChangeListener {}.build()
                    assertTrue(manager.requestAudioFocus(request) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    try {
                        SystemClock.sleep(2000)
                        automation.adoptShellPermissionIdentity(android.Manifest.permission.MEDIA_CONTENT_CONTROL)
                        try {
                            val sessions = instrumentation.targetContext.getSystemService(android.media.session.MediaSessionManager::class.java)
                                .getActiveSessions(null).filter { it.packageName == packageName }
                            assertTrue(sessions.isNotEmpty(), "release MediaSession exists")
                            assertTrue(sessions.all { it.playbackState?.state == android.media.session.PlaybackState.STATE_PAUSED },
                                "OS focus interruption pauses the release")
                            record("real OS audio-focus interruption paused the release session")
                        } finally { automation.dropShellPermissionIdentity() }
                    } finally { manager.abandonAudioFocusRequest(request) }
                    launch()
                }
                "sleep" -> SystemClock.sleep(step.getLong("ms").also { require(it in 0..60000) })
                "snapshot" -> {
                    assertTrue(nodes().isNotEmpty(), "Capture must belong to the selected app")
                    val name = step.getString("name").also { require(it.matches(Regex("[a-zA-Z0-9-]+"))) }
                    val dir = File(instrumentation.targetContext.cacheDir, "release-acceptance").apply { mkdirs() }
                    File(dir, "$name.txt").writeText(nodes().joinToString("\n") {
                        val bounds = Rect().also(it::getBoundsInScreen)
                        "${labels(it)} checked=${it.isChecked} selected=${it.isSelected} focused=${it.isFocused} range=${it.rangeInfo?.current} $bounds"
                    })
                    requireNotNull(automation.takeScreenshot()) { "Native window screenshot unavailable" }.let { bitmap ->
                        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        finally { bitmap.recycle() }
                    }
                    record("captured $name")
                }
                "seek" -> {
                    val slider = awaitNode("playback range") { it.rangeInfo != null }
                    val range = requireNotNull(slider.rangeInfo)
                    val fraction = step.getDouble("fraction").toFloat().also { require(it in 0f..1f) }
                    assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                        Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, range.min + fraction * (range.max - range.min)) }))
                    await("playback seek") { kotlin.math.abs(progress() - fraction) < .03f }
                }
                "advance" -> {
                    val before = progress()
                    assertTrue(before >= 0, "visible playback range")
                    await("moving playback progress") { progress() > before + 0.001f }
                    record("playback progress advanced")
                }
                else -> error("Unsupported acceptance operation")
            }
        }
    }

    private fun launch() = instrumentation.targetContext.startActivity(Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(packageName, if (packageName == "com.tailscale.ipn") "com.tailscale.ipn.MainActivity" else "app.naviamp.android.MainActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })

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

    private data class NativeSession(
        val state: Int?, val queueCount: Int, val mediaId: String?, val positionMillis: Long?,
        val queueItems: List<String>, val title: String?, val queueIndex: Long?,
    )

    private fun nativeSession(): NativeSession? {
        automation.adoptShellPermissionIdentity(android.Manifest.permission.MEDIA_CONTENT_CONTROL)
        try {
            val controller = instrumentation.targetContext.getSystemService(android.media.session.MediaSessionManager::class.java)
                .getActiveSessions(null).firstOrNull { it.packageName == packageName } ?: return null
            return NativeSession(
                controller.playbackState?.state,
                controller.queue?.size ?: 0,
                controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID),
                controller.playbackState?.position,
                // History is presented in reverse order, and the current item's media ID differs
                // from its history ID. Compare the title multiset; retain duplicate occurrences.
                controller.queue.orEmpty().map { it.description.title.toString() },
                controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
                controller.playbackState?.activeQueueItemId,
            )
        } finally { automation.dropShellPermissionIdentity() }
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
        error("Timed out: $description; acceptance app labels=${nodes().flatMap(::labels).distinct()}")
    }

    private fun record(message: String) = instrumentation.sendStatus(2, Bundle().apply {
        putString("stream", "\nRelease acceptance: $message\n")
    })
}
