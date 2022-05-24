package com.github.phillco.talonjetbrains.cursorless

import kotlinx.serialization.Serializable

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
class CursorlessCursor {
    var line = 0
    var character = 0
}