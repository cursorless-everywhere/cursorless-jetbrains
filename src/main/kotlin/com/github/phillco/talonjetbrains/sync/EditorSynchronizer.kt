package com.github.phillco.talonjetbrains.sync

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import io.sentry.Sentry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Point
import java.nio.file.Files
import java.nio.file.Paths

// https://github.com/Kotlin/kotlinx.serialization/issues/993

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class OverallState(
    val pid: Long,
    val ideProduct: String,
    val ideVersion: String,
    val pluginVersion: String?,
    val activeEditor: EditorState?,
    val allEditors: List<FileEditorState>?,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class EditorState(
    val path: String?,
    val project: ProjectState?,
    val firstVisibleLine: Int,
    val lastVisibleLine: Int,

    val cursors: List<Cursor>
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class FileEditorState(
    val path: String?,
    val name: String?,
    val isModified: Boolean,
    val isValid: Boolean,
//    val project: ProjectState,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class ProjectState(
    val name: String,
    val basePath: String?,
    val repos: List<RepoState>,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class RepoState(
    val root: String,
    val vcsType: String,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class Cursor(
    val line: Int,
    val column: Int,
)

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

    val cursors = editor.caretModel.allCarets.map { c ->
        Cursor(
            c.logicalPosition.line,
            c.logicalPosition.column
        )
    }

    val ve = editor.scrollingModel.visibleArea

    return EditorState(
        currentFile,
        project?.let { serializeProject(it) },
        editor.xyToLogicalPosition(Point(ve.x, ve.y)).line,
        editor.xyToLogicalPosition(Point(ve.x, ve.y + ve.height)).line,
        cursors
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
        ApplicationNamesInfo.getInstance().fullProductName,
        ApplicationInfo.getInstance().fullVersion,
        PluginManagerCore.getPlugin(PluginId.findId("com.github.phillco.talonjetbrains"))?.version,
        editor?.let { serializeEditor(it) },
        allEditors?.map { x -> serializeFileEditor(x) }
    )
}

var hasShutdown = false

fun serializeEditorStateToFile() {
    try {

        val pid = ProcessHandle.current().pid()

        val root = Paths.get(System.getProperty("user.home"), ".jb-state")
        val path = root.resolve("$pid.json")

        val state = serializeOverallState()

        if (hasShutdown) {
            println("Skipping writing state to: $path; shutdown initiated")
            return
        }

        Files.createDirectories(root)
        val json = Json.encodeToString(state)
        Files.writeString(path, json)
        Files.writeString(
            root.resolve("${ApplicationNamesInfo.getInstance().fullProductName}.json"),
            json
        )
        Files.writeString(root.resolve("latest.json"), json)

        // Also write the cursorless state
        val cursorlessRoot = Paths.get(System.getProperty("user.home"), ".cursorless")
        Files.writeString(cursorlessRoot.resolve("editor-state.json"), json)

        println("Wrote state to: $path")
    } catch (e: Exception) {
        e.printStackTrace()
        Sentry.captureException(e)
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

            val cursorlessRoot = Paths.get(System.getProperty("user.home"), ".cursorless")
            Files.delete(cursorlessRoot.resolve("editor-state.json"))
            println("Deleted: $path")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Sentry.captureException(e)
    }
}
