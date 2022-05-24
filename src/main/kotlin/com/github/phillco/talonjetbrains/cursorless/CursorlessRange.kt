package com.github.phillco.talonjetbrains.cursorless

import kotlinx.serialization.Serializable

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
class CursorlessRange {
    var start: CursorlessCursor? = null
    var end: CursorlessCursor? = null
}