package app.naviamp.desktop.settings

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun interface DesktopDirectoryPicker {
    fun chooseDirectory(currentPath: String, title: String): String?
}

fun interface DesktopDocumentPicker {
    fun chooseDocument(currentPath: String, title: String): String?
}

/** Native directory selection with a Swing fallback when the preferred OS mechanism is absent. */
class DesktopNativeDirectoryPicker : DesktopDirectoryPicker {
    override fun chooseDirectory(currentPath: String, title: String): String? =
        when (
            val result = when {
                isMacOs() -> chooseMacDirectory(currentPath, title)
                isWindows() -> chooseWindowsDirectory(currentPath, title)
                else -> chooseLinuxDirectory(currentPath, title)
            }
        ) {
            is DirectoryPickerResult.Selected -> result.file.absolutePath
            DirectoryPickerResult.Cancelled -> null
            DirectoryPickerResult.Unavailable -> chooseSwingDirectory(currentPath, title)?.absolutePath
        }
}

/** Native JSON document selection used for one-off settings imports. */
class DesktopNativeDocumentPicker : DesktopDocumentPicker {
    override fun chooseDocument(currentPath: String, title: String): String? =
        when (
            val result = when {
                isMacOs() -> chooseMacDocument(currentPath, title)
                isWindows() -> chooseWindowsDocument(currentPath, title)
                else -> chooseLinuxDocument(currentPath, title)
            }
        ) {
            is DirectoryPickerResult.Selected -> result.file.absolutePath
            DirectoryPickerResult.Cancelled -> null
            DirectoryPickerResult.Unavailable -> chooseSwingDocument(currentPath, title)?.absolutePath
        }
}

private sealed interface DirectoryPickerResult {
    data class Selected(val file: File) : DirectoryPickerResult
    data object Cancelled : DirectoryPickerResult
    data object Unavailable : DirectoryPickerResult
}

private fun chooseMacDirectory(currentPath: String, title: String): DirectoryPickerResult {
    val previous = System.getProperty(MacDirectoryDialogProperty)
    System.setProperty(MacDirectoryDialogProperty, "true")
    return try {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.directory = currentPath
        dialog.isVisible = true
        val selected = dialog.file ?: return DirectoryPickerResult.Cancelled
        DirectoryPickerResult.Selected(File(dialog.directory, selected))
    } catch (_: Throwable) {
        DirectoryPickerResult.Unavailable
    } finally {
        if (previous == null) {
            System.clearProperty(MacDirectoryDialogProperty)
        } else {
            System.setProperty(MacDirectoryDialogProperty, previous)
        }
    }
}

private fun chooseMacDocument(currentPath: String, title: String): DirectoryPickerResult {
    val previous = System.getProperty(MacDirectoryDialogProperty)
    System.setProperty(MacDirectoryDialogProperty, "false")
    return try {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.directory = File(currentPath).let { if (it.isDirectory) it.path else it.parent }
        dialog.filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val selected = dialog.file ?: return DirectoryPickerResult.Cancelled
        DirectoryPickerResult.Selected(File(dialog.directory, selected))
    } catch (_: Throwable) {
        DirectoryPickerResult.Unavailable
    } finally {
        if (previous == null) System.clearProperty(MacDirectoryDialogProperty)
        else System.setProperty(MacDirectoryDialogProperty, previous)
    }
}

private fun chooseWindowsDirectory(currentPath: String, title: String): DirectoryPickerResult {
    val selectedPath = runNativeDirectoryPicker(
        "powershell",
        "-NoProfile",
        "-STA",
        "-Command",
        """
        Add-Type -AssemblyName System.Windows.Forms;
        ${'$'}dialog = New-Object System.Windows.Forms.FolderBrowserDialog;
        ${'$'}dialog.Description = '${title.replace("'", "''")}';
        ${'$'}dialog.SelectedPath = '${currentPath.replace("'", "''")}';
        if (${'$'}dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            [Console]::Out.Write(${'$'}dialog.SelectedPath)
        }
        """.trimIndent(),
    )
    return when (selectedPath) {
        null -> DirectoryPickerResult.Unavailable
        "" -> DirectoryPickerResult.Cancelled
        else -> DirectoryPickerResult.Selected(File(selectedPath))
    }
}

private fun chooseWindowsDocument(currentPath: String, title: String): DirectoryPickerResult {
    val selectedPath = runNativeDirectoryPicker(
        "powershell",
        "-NoProfile",
        "-STA",
        "-Command",
        """
        Add-Type -AssemblyName System.Windows.Forms;
        ${'$'}dialog = New-Object System.Windows.Forms.OpenFileDialog;
        ${'$'}dialog.Title = '${title.replace("'", "''")}';
        ${'$'}dialog.InitialDirectory = '${currentPath.replace("'", "''")}';
        ${'$'}dialog.Filter = 'JSON documents (*.json)|*.json';
        if (${'$'}dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            [Console]::Out.Write(${'$'}dialog.FileName)
        }
        """.trimIndent(),
    )
    return selectedPath.toPickerResult()
}

private fun chooseLinuxDirectory(currentPath: String, title: String): DirectoryPickerResult {
    runNativeDirectoryPicker(
        "zenity",
        "--file-selection",
        "--directory",
        "--title=$title",
        "--filename=$currentPath/",
    )?.let { result ->
        return if (result.isBlank()) DirectoryPickerResult.Cancelled
        else DirectoryPickerResult.Selected(File(result))
    }
    runNativeDirectoryPicker(
        "kdialog",
        "--getexistingdirectory",
        currentPath,
        "--title",
        title,
    )?.let { result ->
        return if (result.isBlank()) DirectoryPickerResult.Cancelled
        else DirectoryPickerResult.Selected(File(result))
    }
    return DirectoryPickerResult.Unavailable
}

private fun chooseLinuxDocument(currentPath: String, title: String): DirectoryPickerResult {
    runNativeDirectoryPicker(
        "zenity",
        "--file-selection",
        "--title=$title",
        "--filename=$currentPath/",
        "--file-filter=JSON documents | *.json",
    )?.let { return it.toPickerResult() }
    runNativeDirectoryPicker(
        "kdialog",
        "--getopenfilename",
        currentPath,
        "*.json|JSON documents",
        "--title",
        title,
    )?.let { return it.toPickerResult() }
    return DirectoryPickerResult.Unavailable
}

private fun String?.toPickerResult(): DirectoryPickerResult = when (this) {
    null -> DirectoryPickerResult.Unavailable
    "" -> DirectoryPickerResult.Cancelled
    else -> DirectoryPickerResult.Selected(File(this))
}

private fun runNativeDirectoryPicker(vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(*command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        when (process.waitFor()) {
            0 -> output
            1 -> ""
            else -> null
        }
    }.getOrNull()

private fun chooseSwingDirectory(currentPath: String, title: String): File? {
    val chooser = JFileChooser(File(currentPath))
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = title
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseSwingDocument(currentPath: String, title: String): File? {
    val initial = File(currentPath)
    val chooser = JFileChooser(if (initial.isDirectory) initial else initial.parentFile)
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    chooser.dialogTitle = title
    chooser.isAcceptAllFileFilterUsed = false
    chooser.fileFilter = FileNameExtensionFilter("JSON documents", "json")
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun isMacOs(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

private const val MacDirectoryDialogProperty = "apple.awt.fileDialogForDirectories"
