package app.naviamp.android

import java.io.File

class AndroidFileTreeCleaner {
    fun clearDirectoryContents(directory: File) {
        if (!directory.exists()) return
        directory.walkBottomUp()
            .filter { it != directory }
            .forEach { it.delete() }
    }
}
