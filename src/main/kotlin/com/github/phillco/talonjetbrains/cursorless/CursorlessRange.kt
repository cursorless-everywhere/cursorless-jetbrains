package com.github.phillco.talonjetbrains.cursorless

import kotlinx.serialization.Serializable

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
class CursorlessRange {
    // TODO: move to CursorlessRange2
    var start: VSCodePosition? = null
    var end: VSCodePosition? = null
}
