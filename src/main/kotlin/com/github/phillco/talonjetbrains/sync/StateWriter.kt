package com.github.phillco.talonjetbrains.sync

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Point
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

// ================================================================================
// StateWriter
//
// This writes out the current state of the editor to a file whenever there are changes
// (projects opened, tabs switched, cursors moved, contents changed, etc.)
//
// This allows Talon (or the Visual Studio Code sidecar) to watch efficiently for changes.
// ================================================================================

// This is increased every time any change is made.
var serial: Long = 0

var hasShutdown = false

/**
 * Maps files in the editor to temporary files that we pass to the Cursorless sidecar.
 *
 * There are two reasons that we do this:
 *
 * - In-memory changes are written immediately to the temporary file, regardless of whether the user has saved the document. Otherwise it wouldn't be possible to run Cursorless on unsaved changes.
 *
 * - When running commands, Cursorless only operates on the temporary file, not the real file. This makes it much easier to diff the changes the command made, and also prevents conflicts between the in-memory document and the file system.
 *
 * TODO(pcohen): we need to manually clean these up as we switch from file to file, not just rely on
 * the temporary directory getting wiped
 */
var cursorlessTempFiles = mutableMapOf<String, Path>()

private val log = logger<OverallState>()

fun recentProjects(): Map<String, String> {
//    val start = System.currentTimeMillis()
    try {
        val recentProjectsManager = RecentProjectsManager.getInstance()

        val recentProjectsManagerBase =
            (recentProjectsManager as RecentProjectsManagerBase)

        val map = mutableMapOf<String, String>()

        recentProjectsManagerBase.getRecentPaths().forEach { path ->
            val name = recentProjectsManagerBase.getProjectName(path)
            map[name] = path
        }


//    println("recentProjects took ${System.currentTimeMillis() - start}ms")

        return map
    } catch (e: Exception) {
        //2023-06-04 15:56:39,205 [  10091] SEVERE - #c.i.o.p.i.ProjectManagerImpl - Method 'com.intellij.ide.RecentProjectsManager com.intellij.ide.RecentProjectsManager.getInstance()' must be InterfaceMethodref constant
        //java.lang.IncompatibleClassChangeError: Method 'com.intellij.ide.RecentProjectsManager com.intellij.ide.RecentProjectsManager.getInstance()' must be InterfaceMethodref constant
        log.error("Error getting recent projects", e)
        return mapOf()
    }
}

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

fun toEditor(fileEditor: FileEditor): Editor =
    (fileEditor as TextEditor).editor

fun openFiles(project: Project): List<String> {
    val k =
        (FileEditorManager.getInstance(project) as FileEditorManagerImpl)

//    return emptyList()
    return k.selectionHistory.map { it.first.path }
}

fun recentFiles(project: Project): List<String> {
    return EditorHistoryManager.getInstance(project).fileList.map { it.path }
        .reversed()
}

fun serializeEditor(editor: Editor, active: Boolean): EditorState {
    val project = editor.project
    val document = editor.document

    val currentFile =
        FileDocumentManager.getInstance().getFile(document)?.path

    var temporaryFilePath: Path? = null
    if (currentFile != null) {
        if (!cursorlessTempFiles.containsKey(currentFile)) {
            val tf = kotlin.io.path.createTempFile(
                "cursorless-${File(currentFile).nameWithoutExtension}-",
                ".${File(currentFile).extension}"
            )
            cursorlessTempFiles.put(
                currentFile,
                tf
            )
        }
        temporaryFilePath = cursorlessTempFiles.get(currentFile)

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
        active,
        project?.let { serializeProject(it) },
        editor.xyToLogicalPosition(Point(ve.x, ve.y)).line,
        editor.xyToLogicalPosition(Point(ve.x, ve.y + ve.height)).line,
        cursors,
        selections,
        openFiles(project!!),
        recentFiles(project),
    )
}

/**
 * Serializes all open and visible editors (splits) for the given project.
 *
 * It does not include non visible tabs inside of those splits.
 */
fun serializeAllEditors(project: Project): List<EditorState> {
    val femx = FileEditorManagerEx.getInstanceEx(project)

    val allEditors =
        femx?.windows?.map { window ->
            val selectedFile = window.selectedFile


            // TODO(pcohen): this doesn't properly support opening the same window
            // across multiple splits
            val editors = femx.getEditors(selectedFile!!)
            toEditor(editors[0])
        }

    return allEditors?.let { editors ->
        editors.map { it ->
            serializeEditor(
                it,
                it == getEditor()
            )
        }
    } ?: listOf()
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
    val project = getProject()
    val allEditors = getFileEditorManager()?.allEditors

    return OverallState(
        ProcessHandle.current().pid(),
        serial,
        ApplicationNamesInfo.getInstance().fullProductName,
        ApplicationInfo.getInstance().fullVersion,
        PluginManagerCore.getPlugin(PluginId.findId("com.github.phillco.talonjetbrains"))?.version,
        editor?.let { serializeEditor(it, true) },
        project?.let { p -> serializeAllEditors(p) } ?: listOf(),
        recentProjects()
        // NOTE(pcohen): removed for now; not very useful
//        allEditors?.map { x -> serializeFileEditor(x) }
    )
}

fun markEditorChange(source: String) {
    serial += 1
    log.info("serial bumped to $serial ($source)")
    serializeEditorStateToFile()
}

/**
 * Returns whether we are the "active Cursorless editor" application and should show hats / receive commands / etc.
 * This is to allow multiple applications to support Cursorless.
 *
 * This is set by Talon when switching between different JetBrains editors.
 */
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
    }
}
