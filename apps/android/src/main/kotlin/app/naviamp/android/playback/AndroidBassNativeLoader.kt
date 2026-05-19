package app.naviamp.android.playback

import android.util.Log

object AndroidBassNativeLoader {
    private const val Tag = "NaviampBass"

    private val libraries = listOf(
        "bass",
        "bass_ssl",
        "bass_aac",
        "bass_ac3",
        "bass_fx",
        "bass_mpc",
        "bass_tta",
        "bassalac",
        "bassape",
        "bassdsd",
        "bassflac",
        "basshls",
        "bassloud",
        "bassmidi",
        "bassmix",
        "bassopus",
        "basswebm",
        "basswv",
    )

    @Volatile
    private var cachedReport: AndroidBassLoadReport? = null

    fun loadBundledLibraries(): AndroidBassLoadReport =
        cachedReport ?: synchronized(this) {
            cachedReport ?: loadLibraries().also { report ->
                cachedReport = report
                Log.i(
                    Tag,
                    "BASS load available=${report.available}, loaded=${report.loadedLibraries.size}, " +
                        "failed=${report.failedLibraries.size}",
                )
                report.failedLibraries.forEach { failure ->
                    Log.w(Tag, "Failed to load ${failure.name}: ${failure.message}")
                }
            }
        }

    private fun loadLibraries(): AndroidBassLoadReport {
        val loaded = mutableListOf<String>()
        val failed = mutableListOf<AndroidBassLoadFailure>()
        libraries.forEach { library ->
            runCatching {
                System.loadLibrary(library)
            }.onSuccess {
                loaded += library
            }.onFailure { error ->
                failed += AndroidBassLoadFailure(
                    name = library,
                    message = error.message ?: error::class.java.simpleName,
                )
            }
        }
        return AndroidBassLoadReport(
            loadedLibraries = loaded,
            failedLibraries = failed,
        )
    }
}

data class AndroidBassLoadReport(
    val loadedLibraries: List<String>,
    val failedLibraries: List<AndroidBassLoadFailure>,
) {
    val available: Boolean = loadedLibraries.contains("bass")
}

data class AndroidBassLoadFailure(
    val name: String,
    val message: String,
)
