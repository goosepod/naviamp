package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun NaviampUpdateCheckEffect(
    enabled: Boolean,
    currentVersion: String,
    onUpdateAvailable: (NaviampAvailableUpdate) -> Unit,
) {
    LaunchedEffect(enabled, currentVersion) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            runCatching { checkForNaviampUpdate(currentVersion, NaviampUpdateHttpClient) }
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
private val NaviampUpdateHttpClient = KtorSharedHttpClient()
