package com.github.phillco.talonjetbrains.cursorless

import com.intellij.openapi.editor.LogicalPosition
import kotlinx.serialization.Serializable

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
class VSCodePosition {
    var line = 0
    var character = 0

    fun toLogicalPosition(): LogicalPosition = LogicalPosition(line, character)
}
