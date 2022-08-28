package com.github.phillco.talonjetbrains.cursorless

import com.intellij.openapi.editor.LogicalPosition
import kotlinx.serialization.Serializable

@Serializable
class VSCodePosition {
    var line = 0
    var character = 0

    fun toLogicalPosition(): LogicalPosition = LogicalPosition(line, character)
}
