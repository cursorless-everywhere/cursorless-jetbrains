package com.github.phillco.talonjetbrains.sync

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import io.sentry.Sentry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths

// https://github.com/Kotlin/kotlinx.serialization/issues/993

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class OverallState(
    val pid: Long,
    val activeEditor: EditorState,
    val allEditors: List<FileEditorState>?,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class EditorState(
    val path: String?,
    val project: ProjectState?,

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

fun getProject(): Project {
    return IdeFocusManager.findInstance().lastFocusedFrame!!.project!!
}

fun getEditor(): Editor {
    return getFileEditorManager()?.selectedTextEditor!!
}

fun getFileEditorManager(): FileEditorManager? {
    return FileEditorManager.getInstance(getProject())
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

    return EditorState(currentFile,
        project?.let { serializeProject(it) }, cursors
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
        serializeEditor(editor),
        allEditors?.map { x -> serializeFileEditor(x) }
    )

}

fun serializeEditorStateToFile() {
    try {
        val state = serializeOverallState()

        val pid = ProcessHandle.current().pid()
        val json = Json.encodeToString(state)

        val root = Paths.get(System.getProperty("user.home"), ".jb-state")
        Files.createDirectories(root)

        val path = root.resolve("$pid.json")
        Files.writeString(path, json)
        Files.writeString(root.resolve("latest.json"), json)
        println("Wrote state to: $path")
    } catch (e: Exception) {
        e.printStackTrace()
        Sentry.captureException(e)
    }
}