package com.github.phillco.talonjetbrains.sync

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import io.sentry.Sentry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Point
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

// ================================================================================
// EditorSynchronizer
//
// This synchronizes the current state of the editor to a file whenever there are changes
// (projects opened, tabs switched, cursors moved, contents changed, etc.)
//
// This allows Talon (or the Visual Studio Code sidecar) to watch efficiently for changes.
// ================================================================================

// This is increased every time any change is made.
var serial: Long = 0

var hasShutdown = false

var tempFiles = mutableMapOf<String, Path>()
var tempFilesInverted = mutableMapOf<Path, String>()

private val log = logger<OverallState>()

fun getProject(): Project? {
    return IdeFocusManager.findInstance().lastFocusedFrame?.project
}

fun getEditor(): Editor? {
    return getFileEditorManager()?.selectedTextEditor
}

fun getFileEditorManager(): FileEditorManager? {
    return getProject()?.let { FileEditorManager.getInstance(it) }
}

fun serializeProject(project: Project): ProjectState {
    val repos =
        VcsRepositoryManager.getInstance(project).repositories.map { repo ->
            RepoState(repo.root.path, repo.vcs.name.lowercase())
        }
    return ProjectState(project.name, project.basePath, repos)
}

fun serializeEditor(editor: Editor): EditorState {
    val project = editor.project
    val document = editor.document

    val currentFile =
        FileDocumentManager.getInstance().getFile(document)?.path

    var temporaryFilePath: Path? = null
    if (currentFile != null) {
        if (!tempFiles.containsKey(currentFile)) {
            val tf = kotlin.io.path.createTempFile(
                "cursorless-${File(currentFile).nameWithoutExtension}-",
                ".${File(currentFile).extension}"
            )
            tempFiles.put(
                currentFile,
                tf
            )
            tempFilesInverted.put(tf, currentFile)
        }
        temporaryFilePath = tempFiles.get(currentFile)

        Files.writeString(temporaryFilePath, document.charsSequence)
    }

    val cursors = editor.caretModel.allCarets.map { c ->
        Cursor(
            c.logicalPosition.line,
            c.logicalPosition.column
        )
    }

    val selections =
        editor.caretModel.caretsAndSelections.map { selectionFromCaretState(it) }

    val ve = editor.scrollingModel.visibleArea

    return EditorState(
        currentFile,
        temporaryFilePath?.absolutePathString(),
        project?.let { serializeProject(it) },
        editor.xyToLogicalPosition(Point(ve.x, ve.y)).line,
        editor.xyToLogicalPosition(Point(ve.x, ve.y + ve.height)).line,
        cursors,
        selections
    )
}

fun serializeFileEditor(editor: FileEditor): FileEditorState {
    return FileEditorState(
        editor.file?.path,
        editor.file?.name,
        editor.isModified,
        editor.isValid
    )
}

fun serializeOverallState(): OverallState {
    val editor = getEditor()
    val allEditors = getFileEditorManager()?.allEditors

    return OverallState(
        ProcessHandle.current().pid(),
        serial,
        ApplicationNamesInfo.getInstance().fullProductName,
        ApplicationInfo.getInstance().fullVersion,
        PluginManagerCore.getPlugin(PluginId.findId("com.github.phillco.talonjetbrains"))?.version,
        editor?.let { serializeEditor(it) },
        allEditors?.map { x -> serializeFileEditor(x) }
    )
}

fun markEditorChange(source: String) {
    serial += 1
    log.info("serial bumped to $serial ($source)")
    serializeEditorStateToFile()
}

fun isActiveCursorlessEditor(): Boolean {
    val path =
        Paths.get(System.getProperty("user.home"), ".cursorless")
            .resolve("primary-editor-pid")

    try {
        return Files.readString(path).trim().toLong() == ProcessHandle.current()
            .pid()
    } catch (e: Exception) {
        return false
    }
}

fun stateFilePath(): Path {
    val pid = ProcessHandle.current().pid()
    val root = Paths.get(System.getProperty("user.home"), ".jb-state")
    return root.resolve("$pid.json")
}

fun serializeEditorStateToFile(): Path? {
    try {
        val root = Paths.get(System.getProperty("user.home"), ".jb-state")
        val path = stateFilePath()

        val state = serializeOverallState()

        if (hasShutdown) {
            println("Skipping writing state to: $path; shutdown initiated")
            return null
        }

        Files.createDirectories(root)
        val json = Json.encodeToString(state)
        Files.writeString(path, json)

        // Create an alias to the current product, for simplicity when debugging on the command line.
        Files.writeString(
            root.resolve("${ApplicationNamesInfo.getInstance().fullProductName}.pid"),
            "${ProcessHandle.current().pid()}"
        )
        Files.writeString(
            root.resolve("latest.pid"),
            "${ProcessHandle.current().pid()}"
        )

        // NOTE(pcohen): deprecate these
        Files.writeString(root.resolve("latest.json"), json)
        Files.writeString(
            root.resolve("${ApplicationNamesInfo.getInstance().fullProductName}.json"),
            json
        )

        // TODO(pcohen): only write this when debugging
        Files.writeString(
            root.resolve("pid"),
            "${ProcessHandle.current().pid()}"
        )

        // Also write the cursorless state
        if (isActiveCursorlessEditor()) {
            val cursorlessRoot =
                Paths.get(System.getProperty("user.home"), ".cursorless")
            Files.writeString(cursorlessRoot.resolve("editor-state.json"), json)
        }

//        println("Wrote state to: $path")
        return path
    } catch (e: Exception) {
        e.printStackTrace()
        Sentry.captureException(e)
        return null
    }
}

fun markHasShutdown() {
//    hasShutdown = true
//    unlinkStateFile()
}

fun unlinkStateFile() {
    try {
        val pid = ProcessHandle.current().pid()
        val root = Paths.get(System.getProperty("user.home"), ".jb-state")
        val path = root.resolve("$pid.json")
        if (Files.exists(path)) {
            Files.delete(path)
            Files.delete(root.resolve("${ApplicationNamesInfo.getInstance().fullProductName}.json"))
            Files.delete(root.resolve("latest.json"))
            println("Deleted: $path")

            if (isActiveCursorlessEditor()) {
                val cursorlessRoot =
                    Paths.get(System.getProperty("user.home"), ".cursorless")
                Files.delete(cursorlessRoot.resolve("editor-state.json"))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Sentry.captureException(e)
    }
}
