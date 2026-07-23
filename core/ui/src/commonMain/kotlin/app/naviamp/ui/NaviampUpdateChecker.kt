package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.SharedHttpClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class NaviampAvailableUpdate(
    val version: String,
    val name: String,
    val releaseUrl: String,
)

fun interface NaviampApplicationUpdateChecker {
    suspend fun latestUpdate(currentVersion: String): NaviampAvailableUpdate?
}

class HttpNaviampApplicationUpdateChecker(
    private val client: SharedHttpClient,
) : NaviampApplicationUpdateChecker {
    override suspend fun latestUpdate(currentVersion: String): NaviampAvailableUpdate? =
        checkForNaviampUpdate(currentVersion, client)
}

/** Update discovery is portable product behavior; every host gets the same checker by default. */
fun defaultNaviampApplicationUpdateChecker(): NaviampApplicationUpdateChecker =
    NaviampApplicationUpdateChecker { currentVersion ->
        DefaultNaviampApplicationUpdateChecker.latestUpdate(currentVersion)
    }

private val DefaultNaviampApplicationUpdateChecker by lazy {
    HttpNaviampApplicationUpdateChecker(KtorSharedHttpClient())
}

@Composable
fun NaviampApplicationUpdateEffect(
    enabled: Boolean,
    currentVersion: String,
    checker: NaviampApplicationUpdateChecker?,
) {
    var availableUpdate by remember { mutableStateOf<NaviampAvailableUpdate?>(null) }
    val uriHandler = LocalUriHandler.current
    NaviampUpdateCheckEffect(
        enabled = enabled,
        currentVersion = currentVersion,
        checker = checker,
        onUpdateAvailable = { availableUpdate = it },
    )
    LaunchedEffect(enabled) {
        if (!enabled) availableUpdate = null
    }
    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("Naviamp Update Available") },
            text = {
                Text("${update.name} is available. You are currently running $currentVersion.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        availableUpdate = null
                        uriHandler.openUri(update.releaseUrl)
                    },
                ) {
                    Text("View Release")
                }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) {
                    Text("Later")
                }
            },
        )
    }
}

@Composable
fun NaviampUpdateCheckEffect(
    enabled: Boolean,
    currentVersion: String,
    checker: NaviampApplicationUpdateChecker?,
    onUpdateAvailable: (NaviampAvailableUpdate) -> Unit,
) {
    LaunchedEffect(enabled, currentVersion, checker) {
        if (!enabled || checker == null) return@LaunchedEffect
        while (true) {
            runCatching { checker.latestUpdate(currentVersion) }
                .getOrNull()
                ?.let(onUpdateAvailable)
            delay(NaviampUpdateCheckIntervalMillis)
        }
    }
}

internal suspend fun checkForNaviampUpdate(
    currentVersion: String,
    client: SharedHttpClient,
): NaviampAvailableUpdate? {
    val body = client.get(
        NaviampLatestReleaseApiUrl,
        headers = mapOf("Accept" to "application/vnd.github+json"),
    ) ?: return null
    val release = Json.parseToJsonElement(body).jsonObject
    val tag = release["tag_name"]?.jsonPrimitive?.content ?: return null
    if (!isNewerNaviampVersion(tag, currentVersion)) return null
    return NaviampAvailableUpdate(
        version = tag,
        name = release["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: tag,
        releaseUrl = release["html_url"]?.jsonPrimitive?.content ?: NaviampReleasesUrl,
    )
}

internal fun isNewerNaviampVersion(candidate: String, current: String): Boolean {
    val candidateParts = candidate.versionParts() ?: return false
    val currentParts = current.versionParts() ?: return false
    val size = maxOf(candidateParts.size, currentParts.size)
    return (0 until size)
        .map { index -> candidateParts.getOrElse(index) { 0 } to currentParts.getOrElse(index) { 0 } }
        .firstOrNull { (next, installed) -> next != installed }
        ?.let { (next, installed) -> next > installed }
        ?: false
}

private fun String.versionParts(): List<Int>? =
    trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .split('.')
        .takeIf { it.isNotEmpty() }
        ?.map { it.toIntOrNull() ?: return null }

const val NaviampReleasesUrl = "https://github.com/goosepod/naviamp/releases"
private const val NaviampLatestReleaseApiUrl = "https://api.github.com/repos/goosepod/naviamp/releases/latest"
private const val NaviampUpdateCheckIntervalMillis = 24L * 60L * 60L * 1_000L
