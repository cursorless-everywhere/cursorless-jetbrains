package com.github.phillco.talonjetbrains.sync

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import kotlinx.serialization.Serializable

/**
 * Represents the overall state of the editor.
 */
@Serializable
data class OverallState(
    val pid: Long,
    val serial: Long,
    val ideProduct: String,
    val ideVersion: String,
    val pluginVersion: String?,

    // TODO(pcohen): deprecate this; caller should just read find the editor with active=True
    val activeEditor: EditorState?,

    val editors: List<EditorState>,

    val recentProjects: Map<String, String>

//    val allEditors: List<FileEditorState>?
)

/**
 * Represents the state of the primary editor (the one focused).
 */
@Serializable
data class EditorState(
    val path: String?,
    val temporaryFilePath: String?,
    val active: Boolean,
    val project: ProjectState?,
    val firstVisibleLine: Int,
    val lastVisibleLine: Int,

    val cursors: List<Cursor>,
    val selections: List<Selection>,

    val openFiles: List<String>,
    val recentFiles: List<String>,
//    val windowCount: Int,
//    val openFiles: List<String?>?
)

/**
 * Represents the state of a "FileEditor". JetBrains treats non-focused tabs as these lesser
 * "FileEditor"s and provides less information about them compared to `Editor`s (for example,
 * we don't have cursor state).
 */
@Serializable
data class FileEditorState(
    val path: String?,
    val name: String?,
    val isModified: Boolean,
    val isValid: Boolean
//    val project: ProjectState,
)

/**
 * Represents the state of the current project.
 */
@Serializable
data class ProjectState(
    val name: String,
    val basePath: String?,
    val repos: List<RepoState>
)

/**
 * Represents the state of a VCS repository within a project (projects can have multiple VCS roots).
 */
@Serializable
data class RepoState(
    val root: String,
    val vcsType: String
)

/**
 * Represents a single cursor.
 */
@Serializable
data class Cursor(
    val line: Int,
    val column: Int
)

/**
 * Represents a single selection.
 */
@Serializable
data class Selection(
    val start: Cursor?,
    val end: Cursor?,

    // NOTE(pcohen): unlike VS Code, there's no requirement that the cursor position the either the
    // start or end of the selection
    var cursorPosition: Cursor?,

    // NOTE(pcohen): these are provided for convenience for VS Code logic
    val active: Cursor?,
    val anchor: Cursor?
)

// TODO(pcohen): can we put these directly on the data classes?
fun cursorFromLogicalPosition(lp: LogicalPosition): Cursor =
    Cursor(lp.line, lp.column)

fun selectionFromCaretState(lp: CaretState): Selection {
    val start = lp.selectionStart?.let { cursorFromLogicalPosition(it) }
    val end = lp.selectionEnd?.let { cursorFromLogicalPosition(it) }
    val cursor = lp.caretPosition?.let { cursorFromLogicalPosition(it) }

    // provide the "anchor" and "active" for ease of implementation inside of Visual Studio Code
    // note - if the cursor isn't either of these, will return null
    var active: Cursor? = null
    var anchor: Cursor? = null
    if (start == cursor) {
        active = start
        anchor = end
    } else if (end == cursor) {
        active = end
        anchor = start
    }

    return Selection(
        start,
        end,
        cursor,
        active,
        anchor
    )
}
