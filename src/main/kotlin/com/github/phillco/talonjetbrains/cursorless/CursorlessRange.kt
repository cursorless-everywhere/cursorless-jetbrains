package com.github.phillco.talonjetbrains.cursorless

import kotlinx.serialization.Serializable

@Serializable
class CursorlessRange {
    // TODO: move to CursorlessRange2
    var start: VSCodePosition? = null
    var end: VSCodePosition? = null
    var startOffset: Int? = null
    var endOffset: Int? = null
}
