package app.naviamp.android

import android.content.Context
import android.os.Environment
import java.io.File

data class AndroidStorageLocation(
    val id: String,
    val label: String,
    val directory: File,
)

fun androidDownloadStorageLocations(context: Context): List<AndroidStorageLocation> {
    val internal = AndroidStorageLocation(
        id = "internal",
        label = "Internal app storage",
        directory = File(context.filesDir, "downloads"),
    )
    val external = context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC)
        .filterNotNull()
        .distinctBy { it.absolutePath }
        .mapIndexed { index, root ->
            val removable = Environment.isExternalStorageRemovable(root)
            AndroidStorageLocation(
                id = if (removable) "removable-$index" else "device-$index",
                label = if (removable) "SD card" else "Device storage",
                directory = File(root, "Naviamp Downloads"),
            )
        }
    return (listOf(internal) + external).distinctBy { it.directory.absolutePath }
}

fun androidAudioCacheStorageLocations(context: Context): List<AndroidStorageLocation> {
    val internal = AndroidStorageLocation(
        id = "internal",
        label = "Internal app storage",
        directory = File(context.cacheDir, "audio-cache"),
    )
    val external = context.externalCacheDirs
        .filterNotNull()
        .distinctBy { it.absolutePath }
        .mapIndexed { index, root ->
            val removable = Environment.isExternalStorageRemovable(root)
            AndroidStorageLocation(
                id = if (removable) "removable-$index" else "device-$index",
                label = if (removable) "SD card" else "Device storage",
                directory = File(root, "Naviamp Cache"),
            )
        }
    return (listOf(internal) + external).distinctBy { it.directory.absolutePath }
}
