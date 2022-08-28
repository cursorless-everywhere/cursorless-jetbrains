package com.github.phillco.talonjetbrains.cursorless

import com.intellij.openapi.editor.CaretState
import kotlinx.serialization.Serializable

@Serializable
class VSCodeSelection {
    // TODO(pcohen): these should not be nullable
    var anchor: VSCodePosition? = null
    var active: VSCodePosition? = null

    var start: VSCodePosition? = null
    var end: VSCodePosition? = null

    fun toCaretState(): CaretState = CaretState(
        active?.toLogicalPosition(),
        start?.toLogicalPosition(),
        end?.toLogicalPosition()
    )
}
