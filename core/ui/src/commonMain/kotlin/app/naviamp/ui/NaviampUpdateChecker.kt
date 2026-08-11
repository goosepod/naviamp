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
import app.naviamp.domain.settings.ApplicationUpdateChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class NaviampAvailableUpdate(
    val version: String,
    val name: String,
    val releaseUrl: String,
)

fun interface NaviampApplicationUpdateChecker {
    suspend fun latestUpdate(
        currentVersion: String,
        channel: ApplicationUpdateChannel,
    ): NaviampAvailableUpdate?
}

class HttpNaviampApplicationUpdateChecker(
    private val client: SharedHttpClient,
) : NaviampApplicationUpdateChecker {
    override suspend fun latestUpdate(
        currentVersion: String,
        channel: ApplicationUpdateChannel,
    ): NaviampAvailableUpdate? = checkForNaviampUpdate(currentVersion, channel, client)
}

/** Update discovery is portable product behavior; every host gets the same checker by default. */
fun defaultNaviampApplicationUpdateChecker(): NaviampApplicationUpdateChecker =
    NaviampApplicationUpdateChecker { currentVersion, channel ->
        DefaultNaviampApplicationUpdateChecker.latestUpdate(currentVersion, channel)
    }

private val DefaultNaviampApplicationUpdateChecker by lazy {
    HttpNaviampApplicationUpdateChecker(KtorSharedHttpClient())
}

@Composable
fun NaviampApplicationUpdateEffect(
    enabled: Boolean,
    currentVersion: String,
    channel: ApplicationUpdateChannel,
    checker: NaviampApplicationUpdateChecker?,
) {
    var availableUpdate by remember { mutableStateOf<NaviampAvailableUpdate?>(null) }
    val uriHandler = LocalUriHandler.current
    NaviampUpdateCheckEffect(
        enabled = enabled,
        currentVersion = currentVersion,
        channel = channel,
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
    channel: ApplicationUpdateChannel,
    checker: NaviampApplicationUpdateChecker?,
    onUpdateAvailable: (NaviampAvailableUpdate) -> Unit,
) {
    LaunchedEffect(enabled, currentVersion, channel, checker) {
        if (!enabled || checker == null) return@LaunchedEffect
        while (true) {
            runCatching { checker.latestUpdate(currentVersion, channel) }
                .getOrNull()
                ?.let(onUpdateAvailable)
            delay(NaviampUpdateCheckIntervalMillis)
        }
    }
}

internal suspend fun checkForNaviampUpdate(
    currentVersion: String,
    channel: ApplicationUpdateChannel,
    client: SharedHttpClient,
): NaviampAvailableUpdate? {
    val body = client.get(
        NaviampLatestReleaseApiUrl,
        headers = mapOf("Accept" to "application/vnd.github+json"),
    ) ?: return null
    val release = Json.parseToJsonElement(body).jsonArray
        .map { it.jsonObject }
        .filterNot { it["draft"]?.jsonPrimitive?.booleanOrNull == true }
        .filter { channel == ApplicationUpdateChannel.Beta || it["prerelease"]?.jsonPrimitive?.booleanOrNull != true }
        .mapNotNull { candidate ->
            val tag = candidate["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val version = tag.naviampVersion() ?: return@mapNotNull null
            Triple(candidate, tag, version)
        }
        .filter { (_, tag, _) -> isNewerNaviampVersion(tag, currentVersion) }
        .maxByOrNull { (_, _, version) -> version }
        ?: return null
    val (releaseJson, tag) = release
    return NaviampAvailableUpdate(
        version = tag,
        name = releaseJson["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: tag,
        releaseUrl = releaseJson["html_url"]?.jsonPrimitive?.content ?: NaviampReleasesUrl,
    )
}

internal fun isNewerNaviampVersion(candidate: String, current: String): Boolean {
    val candidateVersion = candidate.naviampVersion() ?: return false
    val currentVersion = current.naviampVersion() ?: return false
    return candidateVersion > currentVersion
}

fun defaultApplicationUpdateChannel(currentVersion: String): ApplicationUpdateChannel =
    if (currentVersion.naviampVersion()?.prerelease != null) {
        ApplicationUpdateChannel.Beta
    } else {
        ApplicationUpdateChannel.Stable
    }

private data class NaviampVersion(
    val numbers: List<Int>,
    val prerelease: List<String>?,
) : Comparable<NaviampVersion> {
    override fun compareTo(other: NaviampVersion): Int {
        val numberCount = maxOf(numbers.size, other.numbers.size)
        repeat(numberCount) { index ->
            val comparison = numbers.getOrElse(index) { 0 }.compareTo(other.numbers.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        if (prerelease == null) return if (other.prerelease == null) 0 else 1
        if (other.prerelease == null) return -1
        repeat(maxOf(prerelease.size, other.prerelease.size)) { index ->
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }
}

private fun String.naviampVersion(): NaviampVersion? {
    val value = trim().removePrefix("v").removePrefix("V").substringBefore('+')
    val numberText = value.substringBefore('-')
    val numbers = numberText.split('.').map { it.toIntOrNull() ?: return null }
    if (numbers.isEmpty()) return null
    val prerelease = value.substringAfter('-', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.split('.', '-')
        ?.takeIf { parts -> parts.all { it.isNotBlank() } }
        ?: if ('-' in value) return null else null
    return NaviampVersion(numbers, prerelease)
}

const val NaviampReleasesUrl = "https://github.com/goosepod/naviamp/releases"
private const val NaviampLatestReleaseApiUrl = "https://api.github.com/repos/goosepod/naviamp/releases?per_page=30"
private const val NaviampUpdateCheckIntervalMillis = 24L * 60L * 60L * 1_000L
