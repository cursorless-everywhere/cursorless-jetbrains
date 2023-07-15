package com.github.phillco.talonjetbrains.cursorless

import com.intellij.openapi.editor.Editor
import kotlinx.serialization.Serializable

@Serializable
class CursorlessRange {
    // TODO: move to CursorlessRange2
    var start: VSCodePosition? = null
    var end: VSCodePosition? = null

    // NOTE(pcohen): old keys don't use
    var startOffset: Int? = null
    var endOffset: Int? = null


    fun startOffset(editor: Editor) = editor.logicalPositionToOffset(start?.toLogicalPosition()!!)
    fun endOffset(editor: Editor) = editor.logicalPositionToOffset(end?.toLogicalPosition()!!)
}
